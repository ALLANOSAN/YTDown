package com.example.ytdown.core.infrastructure.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.entities.*

@Database(
    entities = [
        DownloadItemEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3, // Incrementamos a versão para forçar a criação das novas tabelas
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        val ALL_MIGRATIONS = arrayOf<androidx.room.migration.Migration>()
    }
}
