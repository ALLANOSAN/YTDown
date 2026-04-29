package com.example.ytdown.core.infrastructure.persistence

import androidx.room.*
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItemEntity)

    @Update
    suspend fun update(item: DownloadItemEntity)

    @Delete
    suspend fun delete(item: DownloadItemEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadItemEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsSync(): List<DownloadItemEntity>

    // --- Queries de Performance (Migradas do LibraryService.dart) ---

    @Query("SELECT DISTINCT artist FROM downloads WHERE artist IS NOT NULL AND artist != '' ORDER BY artist ASC")
    fun getDistinctArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM downloads WHERE album IS NOT NULL AND album != '' ORDER BY album ASC")
    fun getDistinctAlbums(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchLibrary(query: String): Flow<List<DownloadItemEntity>>
}
