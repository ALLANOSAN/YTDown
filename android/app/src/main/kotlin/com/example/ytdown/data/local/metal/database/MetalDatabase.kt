package com.example.ytdown.data.local.metal.database

import android.content.Context
import androidx.room.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.ytdown.data.local.metal.dao.*
import com.example.ytdown.data.local.metal.entities.*
import kotlinx.coroutines.flow.Flow

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
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MetalDatabase : RoomDatabase() {

    abstract fun artistDao(): MetalArtistDao
    abstract fun albumDao(): MetalAlbumDao
    abstract fun historyDao(): ListeningHistoryDao
    abstract fun profileDao(): MusicProfileDao

    companion object {
        private const val DATABASE_NAME = "metal_database"
        
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
                    
                    // Callback para eventos de banco
                    .addCallback(DatabaseCallback())
                    
                    // Builder de queries compiladas
                    .enableMultiInstanceInvalidation()
                    
                    .build()
                
                INSTANCE = instance
                instance
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
    
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name
    
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = 
        SyncStatus.valueOf(value)
    
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name
    
    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        DownloadStatus.valueOf(value)
    
    @TypeConverter
    fun fromInteractionType(type: InteractionType): String = type.name
    
    @TypeConverter
    fun toInteractionType(value: String): InteractionType =
        InteractionType.valueOf(value)
    
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
    
    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onCreate(db)
        // Criar índices adicionais se necessário
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_metal_artists_country ON metal_artists(country)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_metal_artists_score ON metal_artists(compatibilityScore)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_metal_albums_artist ON metal_albums(artistMbid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_history_artist ON listening_history(artistName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_history_date ON listening_history(listenedAt)")
    }
    
    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onOpen(db)
        // Otimizar pragmas ao abrir
        db.execSQL("PRAGMA journal_mode = TRUNCATE")
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA cache_size = -64000") // 64MB cache
    }
}

/**
 * Extensão para injeção via Hilt
 */
fun provideMetalDatabase(context: Context): MetalDatabase {
    return MetalDatabase.getInstance(context)
}