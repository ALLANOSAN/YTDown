package com.example.ytdown.data.local.metal.database

import android.content.Context
import android.util.Log
import androidx.room.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ytdown.data.local.metal.dao.*
import com.example.ytdown.data.local.metal.entities.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Executors

/**
 * Database do Sistema Metal - Cache Offline Completo
 * 
 * Entidades:
 * - MetalArtistEntity: Artistas descobertos
 * - MetalAlbumEntity: Álbuns com capas
 * - ListeningHistoryEntity: Histórico de escuta
 * - MusicProfileEntity: Perfil musical do usuário
 */
@Database(
    entities = [
        MetalArtistEntity::class,
        MetalAlbumEntity::class,
        ListeningHistoryEntity::class,
        MusicProfileEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MetalDatabase : RoomDatabase() {

    abstract fun artistDao(): MetalArtistDao
    abstract fun albumDao(): MetalAlbumDao
    abstract fun historyDao(): ListeningHistoryDao
    abstract fun profileDao(): MusicProfileDao

    companion object {
        private const val DATABASE_NAME = "metal_database"
        
        /**
         * Migration da versão 1 para 2
         * Adiciona colunas de metadados, status de download e índices necessários
         * Usa estratégia de recriação de tabela para garantir alinhamento exato de schema.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d("MIGRATION", "Iniciando MIGRATION_1_2: Recriação de tabelas para integridade")

                // 1. Recriar metal_albums para garantir nulidade e colunas novas
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS metal_albums_new (
                        mbid TEXT NOT NULL PRIMARY KEY,
                        artistMbid TEXT NOT NULL,
                        artistName TEXT NOT NULL,
                        title TEXT NOT NULL,
                        sortTitle TEXT NOT NULL,
                        releaseYear TEXT,
                        releaseDate TEXT,
                        primaryType TEXT NOT NULL,
                        secondaryTypesJson TEXT NOT NULL,
                        frontCoverUrl TEXT,
                        backCoverUrl TEXT,
                        coverThumbnail250 TEXT,
                        coverThumbnail500 TEXT,
                        format TEXT,
                        country TEXT,
                        barcode TEXT,
                        trackCount INTEGER NOT NULL,
                        durationMs INTEGER,
                        hasLyrics INTEGER NOT NULL,
                        isComplete INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        releaseGroupMbid TEXT,
                        downloadStatus TEXT NOT NULL,
                        localPath TEXT,
                        downloadedAt INTEGER,
                        fileSize INTEGER,
                        isFavorite INTEGER NOT NULL,
                        userRating REAL,
                        playCount INTEGER NOT NULL,
                        lastPlayedAt INTEGER,
                        FOREIGN KEY(artistMbid) REFERENCES metal_artists(mbid) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())

                // Copiar dados existentes de metal_albums
                db.execSQL("""
                    INSERT INTO metal_albums_new (
                        mbid, artistMbid, artistName, title, sortTitle, releaseYear, releaseDate, 
                        primaryType, secondaryTypesJson, trackCount, hasLyrics, isComplete, 
                        cachedAt, lastUpdated, downloadStatus, isFavorite, playCount
                    )
                    SELECT 
                        mbid, artistMbid, artistName, title, sortTitle, releaseYear, releaseDate, 
                        primaryType, secondaryTypesJson, trackCount, hasLyrics, isComplete, 
                        cachedAt, lastUpdated, 
                        COALESCE(downloadStatus, 'NOT_DOWNLOADED'), 
                        COALESCE(isFavorite, 0), 
                        COALESCE(playCount, 0)
                    FROM metal_albums
                """.trimIndent())

                db.execSQL("DROP TABLE metal_albums")
                db.execSQL("ALTER TABLE metal_albums_new RENAME TO metal_albums")

                // Recriar índices de metal_albums
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metal_albums_artistMbid ON metal_albums(artistMbid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metal_albums_releaseYear ON metal_albums(releaseYear)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metal_albums_cachedAt ON metal_albums(cachedAt)")
                
                // 2. Atualizar ListeningHistory
                if (!db.hasColumn("listening_history", "favoriteGenre")) {
                    db.execSQL("ALTER TABLE listening_history ADD COLUMN favoriteGenre TEXT")
                }
                
                // 3. metal_artists: o schema v1 e v2 são idênticos na estrutura da tabela.
                // A única diferença é o novo índice por country. Não recriar a tabela.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_metal_artists_country ON metal_artists(country)"
                )
            }
        }

        @Volatile
        private var INSTANCE: MetalDatabase? = null
        
        fun getInstance(context: Context): MetalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MetalDatabase::class.java,
                    DATABASE_NAME
                )
                    // Configurações de otimização
                    .setJournalMode(JournalMode.TRUNCATE)
                    
                    // Adicionar Migrations aqui
                    .addMigrations(MIGRATION_1_2)
                    
                    // Callback para eventos de banco
                    .addCallback(DatabaseCallback())
                    
                    // Builder de queries compiladas
                    .enableMultiInstanceInvalidation()
                    
                    // Callback para debugging de queries
                    // FIX: Adicionado tipos explícitos para evitar "Cannot infer type"
                    .setQueryCallback(
                        { sqlQuery: String, bindArgs: List<Any?> ->
                            Log.d("ROOM_SQL", "Query: $sqlQuery | Args: $bindArgs")
                        }, 
                        Executors.newSingleThreadExecutor()
                    )
                    
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Helper para verificar a existência de colunas no SQLite.
         * Essencial para migrations resilientes.
         */
        private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
            return try {
                query("PRAGMA table_info($tableName)", emptyArray()).use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex == -1) return false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == columnName) return true
                    }
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Limpa a instância (para testing)
         */
        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
class Converters {
    
    // Enum.valueOf lança para qualquer string fora das constantes. Como isto é
    // TypeConverter, roda a cada leitura da coluna: uma linha com valor de outra
    // versão do app derrubaria a query inteira. Fallback, igual ao toLongList
    // logo abaixo.
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        SyncStatus.entries.firstOrNull { it.name == value } ?: SyncStatus.STALE

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        DownloadStatus.entries.firstOrNull { it.name == value } ?: DownloadStatus.NOT_DOWNLOADED

    @TypeConverter
    fun fromInteractionType(type: InteractionType): String = type.name

    @TypeConverter
    fun toInteractionType(value: String): InteractionType =
        InteractionType.entries.firstOrNull { it.name == value } ?: InteractionType.UNKNOWN
    
    @TypeConverter
    fun fromStringList(list: List<String>): String = 
        list.joinToString("|||")
    
    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split("|||")
    
    @TypeConverter
    fun fromLongList(list: List<Long>): String =
        list.joinToString(",")
    
    @TypeConverter
    fun toLongList(value: String): List<Long> =
        if (value.isBlank()) emptyList() else value.split(",").mapNotNull { it.toLongOrNull() }
}

/**
 * Callback para eventos do banco de dados
 */
private class DatabaseCallback : RoomDatabase.Callback() {
    
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Os índices principais já estão definidos nas entidades @Entity
    }
    
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Otimizar pragmas ao abrir - executados apenas uma vez por conexão
        // FIX: Corrigido "query() candidates are not applicable" e "Unresolved reference 'close'"
        // FIX: Removido "setForeignKeyConstraintsEnabled" (unresolved) e trocado por PRAGMA manual
        try {
            // Habilitar Foreign Keys manualmente no onOpen (robusto para SupportSQLiteDatabase)
            db.execSQL("PRAGMA foreign_keys = ON")
            
            // Usar query(String, Array) com emptyArray() e .use {} para segurança e tipagem
            db.query("PRAGMA synchronous = NORMAL", emptyArray<Any>()).use { _ -> }
            db.query("PRAGMA cache_size = -64000", emptyArray<Any>()).use { _ -> }
        } catch (e: Exception) {
            Log.w("MetalDatabase", "PRAGMA optimization failed: ${e.message}")
        }
    }
}

/**
 * Extensão para injeção via Hilt
 */
fun provideMetalDatabase(context: Context): MetalDatabase {
    return MetalDatabase.getInstance(context)
}