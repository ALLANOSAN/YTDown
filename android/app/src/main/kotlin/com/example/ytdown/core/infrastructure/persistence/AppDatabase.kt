package com.example.ytdown.core.infrastructure.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ytdown.core.domain.SongEntity
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.entities.*

@Database(
    entities = [
        SongEntity::class,
        DownloadItemEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SearchHistoryEntity::class
    ],
    version = 4, // +coluna artworkUrl na tabela downloads (Migration 3->4)
    exportSchema = false
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
