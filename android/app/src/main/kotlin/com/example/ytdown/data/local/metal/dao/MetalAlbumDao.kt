package com.example.ytdown.data.local.metal.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.example.ytdown.data.local.metal.entities.DownloadStatus
import com.example.ytdown.data.local.metal.entities.MetalAlbumEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO completo para Álbuns do sistema Metal
 * 
 * Suporta:
 * - Paging 3 para discografias longas
 * - Busca por artista
 * - Estados de download
 * - Cache inteligente
 */
@Dao
interface MetalAlbumDao {

    // =====================================================
    // PAGING 3
    // =====================================================
    
    /**
     * Álbuns de um artista ordenados por ano
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE artistMbid = :artistMbid
        ORDER BY CASE WHEN releaseYear IS NULL OR releaseYear = '' THEN 1 ELSE 0 END, releaseYear DESC
    """)
    fun getPagedAlbumsByArtist(artistMbid: String): PagingSource<Int, MetalAlbumEntity>
    
    /**
     * Álbuns ordenados por popularidade
     */
    @Query("""
        SELECT * FROM metal_albums
        ORDER BY playCount DESC, releaseYear DESC
    """)
    fun getPagedAlbumsByPopularity(): PagingSource<Int, MetalAlbumEntity>
    
    /**
     * Álbuns não baixados (para sugerir download)
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE downloadStatus = 'NOT_DOWNLOADED'
        ORDER BY releaseYear DESC
    """)
    fun getPagedUndownloadedAlbums(): PagingSource<Int, MetalAlbumEntity>
    
    // =====================================================
    // FLOW
    // =====================================================
    
    /**
     * Observa todos os álbuns de um artista
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE artistMbid = :artistMbid
        ORDER BY CASE WHEN releaseYear IS NULL OR releaseYear = '' THEN 1 ELSE 0 END, releaseYear DESC
    """)
    fun observeAlbumsByArtist(artistMbid: String): Flow<List<MetalAlbumEntity>>
    
    /**
     * Observa álbum específico
     */
    @Query("SELECT * FROM metal_albums WHERE mbid = :mbid")
    fun observeAlbum(mbid: String): Flow<MetalAlbumEntity?>
    
    /**
     * Observa álbuns por status de download
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE downloadStatus = :status
        ORDER BY downloadedAt DESC
    """)
    fun observeAlbumsByStatus(status: DownloadStatus): Flow<List<MetalAlbumEntity>>
    
    /**
     * Observa álbuns baixados recentemente
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE downloadStatus = 'DOWNLOADED'
        ORDER BY downloadedAt DESC
        LIMIT :limit
    """)
    fun observeRecentlyDownloaded(limit: Int = 10): Flow<List<MetalAlbumEntity>>
    
    /**
     * Observa favoritos
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE isFavorite = 1
        ORDER BY lastPlayedAt DESC
    """)
    fun observeFavoriteAlbums(): Flow<List<MetalAlbumEntity>>
    
    // =====================================================
    // QUERIES SINCRONAS
    // =====================================================
    
    /**
     * Busca álbum por MBID
     */
    @Query("SELECT * FROM metal_albums WHERE mbid = :mbid")
    suspend fun getAlbumByMbid(mbid: String): MetalAlbumEntity?
    
    /**
     * Busca álbuns por nome (contém)
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE title LIKE '%' || :query || '%'
        ORDER BY releaseYear DESC
        LIMIT :limit
    """)
    suspend fun searchAlbums(query: String, limit: Int = 20): List<MetalAlbumEntity>
    
    /**
     * Retorna discografia completa de um artista
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE artistMbid = :artistMbid
        ORDER BY CASE WHEN releaseYear IS NULL OR releaseYear = '' THEN 1 ELSE 0 END, releaseYear DESC
    """)
    suspend fun getDiscography(artistMbid: String): List<MetalAlbumEntity>
    
    /**
     * Retorna álbuns por tipo
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE primaryType = :type
        ORDER BY releaseYear DESC
        LIMIT :limit
    """)
    suspend fun getAlbumsByType(type: String, limit: Int = 20): List<MetalAlbumEntity>
    
    /**
     * Retorna álbuns expirados
     */
    @Query("""
        SELECT * FROM metal_albums
        WHERE cachedAt < :expirationTime
        ORDER BY cachedAt ASC
        LIMIT :limit
    """)
    suspend fun getExpiredAlbums(expirationTime: Long, limit: Int = 50): List<MetalAlbumEntity>
    
    /**
     * Conta álbuns por artista
     */
    @Query("SELECT COUNT(*) FROM metal_albums WHERE artistMbid = :artistMbid")
    suspend fun getAlbumCountByArtist(artistMbid: String): Int
    
    /**
     * Conta álbuns por tipo
     */
    @Query("SELECT primaryType, COUNT(*) as count FROM metal_albums GROUP BY primaryType ORDER BY count DESC")
    suspend fun getAlbumCountByType(): List<TypeCount>
    
    // =====================================================
    // INSERT/UPDATE/DELETE
    // =====================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: MetalAlbumEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<MetalAlbumEntity>)
    
    @Update
    suspend fun updateAlbum(album: MetalAlbumEntity)
    
    /**
     * Atualiza status de download
     */
    @Query("""
        UPDATE metal_albums
        SET downloadStatus = :status,
            localPath = :localPath,
            downloadedAt = :downloadedAt,
            fileSize = :fileSize
        WHERE mbid = :mbid
    """)
    suspend fun updateDownloadStatus(
        mbid: String,
        status: DownloadStatus,
        localPath: String? = null,
        downloadedAt: Long? = null,
        fileSize: Long? = null
    )
    
    /**
     * Atualiza play count
     */
    @Query("""
        UPDATE metal_albums
        SET playCount = playCount + 1,
            lastPlayedAt = :timestamp
        WHERE mbid = :mbid
    """)
    suspend fun incrementPlayCount(mbid: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Alterna favorito
     */
    @Query("UPDATE metal_albums SET isFavorite = NOT isFavorite WHERE mbid = :mbid")
    suspend fun toggleFavorite(mbid: String)
    
    /**
     * Avaliação do usuário
     */
    @Query("UPDATE metal_albums SET userRating = :rating WHERE mbid = :mbid")
    suspend fun updateRating(mbid: String, rating: Float?)
    
    @Query("DELETE FROM metal_albums WHERE mbid = :mbid")
    suspend fun deleteAlbum(mbid: String)
    
    /**
     * Remove álbuns expirados
     */
    @Query("DELETE FROM metal_albums WHERE cachedAt < :expirationTime AND downloadStatus = 'NOT_DOWNLOADED'")
    suspend fun deleteExpired(expirationTime: Long): Int
    
    /**
     * Remove álbuns de artista
     */
    @Query("DELETE FROM metal_albums WHERE artistMbid = :artistMbid")
    suspend fun deleteAlbumsByArtist(artistMbid: String)
    
    @Query("DELETE FROM metal_albums")
    suspend fun clearAll()
}

data class TypeCount(
    val primaryType: String,
    val count: Int
)