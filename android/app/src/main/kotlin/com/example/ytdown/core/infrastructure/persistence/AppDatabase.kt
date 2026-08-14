package com.example.ytdown.core.infrastructure.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ytdown.core.domain.SongEntity
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.entities.*

/**
 * Versão do schema. Fora da anotação para o teste de migrações poder lê-la —
 * `@Database` tem retenção BINARY e não é visível por reflexão em runtime.
 *
 * Ao subir esta versão: escreva a Migration correspondente e adicione em
 * [AppDatabase.ALL_MIGRATIONS]. Não há mais fallback destrutivo; sem migração
 * o `AppDatabaseMigrationsTest` quebra o build (antes o banco do usuário era
 * apagado em silêncio).
 */
const val DB_VERSION = 4

@Database(
    entities = [
        SongEntity::class,
        DownloadItemEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SearchHistoryEntity::class
    ],
    version = DB_VERSION, // 4: +coluna artworkUrl na tabela downloads (Migration 3->4)
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun libraryDao(): LibraryDao
    abstract fun songDao(): SongDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Coluna nova e nullable — nenhum dado existente é perdido.
                db.execSQL("ALTER TABLE downloads ADD COLUMN artworkUrl TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_3_4)
    }
}
