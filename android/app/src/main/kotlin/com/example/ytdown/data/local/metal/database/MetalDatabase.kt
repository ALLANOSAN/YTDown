package com.example.ytdown.data.local.metal.database

import android.content.Context
import android.util.Log
import androidx.room.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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