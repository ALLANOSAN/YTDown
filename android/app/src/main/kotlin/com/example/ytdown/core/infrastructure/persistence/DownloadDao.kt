package com.example.ytdown.core.infrastructure.persistence

import androidx.paging.PagingSource
import androidx.room.*
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    // Ordem de inserção (createdAt ASC) = ordem da fila/download.
    // Assim o item que está baixando (1º da playlist) aparece no topo.
    @Query("SELECT * FROM downloads ORDER BY createdAt ASC")
    fun getAllDownloads(): Flow<List<DownloadItemEntity>>

    // Paging3 — carrega por página, na ordem de download (antigo→novo)
    @Query("SELECT * FROM downloads ORDER BY createdAt ASC")
    fun getDownloadsPaged(): PagingSource<Int, DownloadItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItemEntity)

    @Update
    suspend fun update(item: DownloadItemEntity)

    @Delete
    suspend fun delete(item: DownloadItemEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadItemEntity?

    /**
     * Próximo item da fila, na ordem de inserção (createdAt ASC).
     * A fila é drenada por UM único worker foreground; isso garante que o
     * download continue mesmo com a tela off / app em segundo plano.
     */
    @Query("SELECT * FROM downloads WHERE status IN ('pending','queued') ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): DownloadItemEntity?

    /** Resgata itens travados em "downloading" (worker morto sem graça) para "pending". */
    @Query("UPDATE downloads SET status = 'pending', progress = 0.0 WHERE status = 'downloading'")
    suspend fun resetStuckDownloading()

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsSync(): List<DownloadItemEntity>

    @Query("SELECT * FROM downloads WHERE status = 'completed' ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): Flow<List<DownloadItemEntity>>

    @Query("SELECT DISTINCT artist FROM downloads WHERE artist IS NOT NULL AND artist != '' ORDER BY artist ASC")
    fun getDistinctArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM downloads WHERE album IS NOT NULL AND album != '' ORDER BY album ASC")
    fun getDistinctAlbums(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchLibrary(query: String): Flow<List<DownloadItemEntity>>
}
