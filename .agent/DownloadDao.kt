package com.example.ytdown.core.infrastructure.persistence

import androidx.room.*
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItemEntity)

    @Delete
    suspend fun delete(item: DownloadItemEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadItemEntity?
}