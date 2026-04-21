package com.example.ytdown.core.infrastructure.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ytdown.core.domain.DownloadItemEntity

@Database(entities = [DownloadItemEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}