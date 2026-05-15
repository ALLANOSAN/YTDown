package com.example.ytdown.data.local.metal.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.example.ytdown.data.local.metal.entities.MetalArtistEntity
import com.example.ytdown.data.local.metal.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO completo para Artistas do sistema Metal
 * 
 * Suporta:
 * - Paging 3 para carregamento eficiente
 * - Busca por nome
 * - Ordenação por score, país, etc
 * - Sincronização com API
 * - Limpeza de cache expirado
 */
@Dao
interface MetalArtistDao {

    // =====================================================
    // PAGING 3 - Carregamento paginado
    // =====================================================
    
    /**
     * Retorna artistas ordenados por score de compatibilidade
     * Ideal para tela principal de recomendações
     */
    @Query("""
        SELECT * FROM metal_artists
        ORDER BY compatibilityScore DESC
    """)
    fun getPagedArtistsByScore(): PagingSource<Int, MetalArtistEntity>
    
    /**
     * Retorna artistas ordenados por nome (A-Z)
     */
    @Query("""
        SELECT * FROM metal_artists
        ORDER BY name ASC
    """)
    fun getPagedArtistsByName(): PagingSource<Int, MetalArtistEntity>
    
    /**
     * Retorna artistas de um país específico
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE country = :country
        ORDER BY compatibilityScore DESC
    """)
    fun getPagedArtistsByCountry(country: String): PagingSource<Int, MetalArtistEntity>
    
    /**
     * Retorna artistas ativos ordenados por popularidade
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE isActive = 1
        ORDER BY playCount DESC, compatibilityScore DESC
    """)
    fun getPagedActiveArtists(): PagingSource<Int, MetalArtistEntity>
    
    /**
     * Retorna artistas favoritos
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE isFavorite = 1
        ORDER BY lastPlayedAt DESC
    """)
    fun getPagedFavoriteArtists(): PagingSource<Int, MetalArtistEntity>
    
    // =====================================================
    // FLOW - Observação em tempo real
    // =====================================================
    
    /**
     * Observa todos os artistas em tempo real
     */
    @Query("SELECT * FROM metal_artists ORDER BY compatibilityScore DESC")
    fun observeAllArtists(): Flow<List<MetalArtistEntity>>
    
    /**
     * Observa artista específico por MBID
     */
    @Query("SELECT * FROM metal_artists WHERE mbid = :mbid")
    fun observeArtist(mbid: String): Flow<MetalArtistEntity?>
    
    /**
     * Observa artistas por país
     */
    @Query("SELECT * FROM metal_artists WHERE country = :country ORDER BY name ASC")
    fun observeArtistsByCountry(country: String): Flow<List<MetalArtistEntity>>
    
    /**
     * Observa artistas mais jogados
     */
    @Query("""
        SELECT * FROM metal_artists 
        WHERE playCount > 0 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun observeMostPlayed(limit: Int = 10): Flow<List<MetalArtistEntity>>
    
    // =====================================================
    // QUERIES SINCRONAS
    // =====================================================
    
    /**
     * Busca artista por MBID
     */
    @Query("SELECT * FROM metal_artists WHERE mbid = :mbid")
    suspend fun getArtistByMbid(mbid: String): MetalArtistEntity?
    
    /**
     * Busca artistas por nome (contém)
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE name LIKE '%' || :query || '%'
           OR sortName LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN name LIKE :query || '%' THEN 0
                WHEN name LIKE '%' || :query || '%' THEN 1
                ELSE 2
            END,
            compatibilityScore DESC
        LIMIT :limit
    """)
    suspend fun searchArtists(query: String, limit: Int = 20): List<MetalArtistEntity>
    
    /**
     * Busca artistas por tag
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE tagsJson LIKE '%' || :tag || '%'
        ORDER BY compatibilityScore DESC
        LIMIT :limit
    """)
    suspend fun getArtistsByTag(tag: String, limit: Int = 20): List<MetalArtistEntity>
    
    /**
     * Retorna artistas que precisam de sincronização
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE syncStatus = :status
        ORDER BY lastUpdated ASC
        LIMIT :limit
    """)
    suspend fun getArtistsBySyncStatus(status: SyncStatus, limit: Int = 50): List<MetalArtistEntity>
    
    /**
     * Retorna artistas expirados (para refresh)
     */
    @Query("""
        SELECT * FROM metal_artists
        WHERE lastUpdated < :expirationTime
           OR syncStatus = 'STALE'
        ORDER BY lastUpdated ASC
        LIMIT :limit
    """)
    suspend fun getExpiredArtists(expirationTime: Long, limit: Int = 50): List<MetalArtistEntity>
    
    /**
     * Conta total de artistas
     */
    @Query("SELECT COUNT(*) FROM metal_artists")
    suspend fun getArtistCount(): Int
    
    /**
     * Conta artistas por país
     */
    @Query("SELECT country, COUNT(*) as count FROM metal_artists WHERE country IS NOT NULL GROUP BY country ORDER BY count DESC")
    suspend fun getArtistCountByCountry(): List<CountryCount>
    
    // =====================================================
    // INSERT/UPDATE/DELETE
    // =====================================================
    
    /**
     * Insere artista (ou substitui se existir)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: MetalArtistEntity)
    
    /**
     * Insere múltiplos artistas em batch
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<MetalArtistEntity>)
    
    /**
     * Atualiza artista
     */
    @Update
    suspend fun updateArtist(artist: MetalArtistEntity)
    
    /**
     * Atualiza play count
     */
    @Query("""
        UPDATE metal_artists 
        SET playCount = playCount + 1, 
            lastPlayedAt = :timestamp
        WHERE mbid = :mbid
    """)
    suspend fun incrementPlayCount(mbid: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Alterna favorito
     */
    @Query("UPDATE metal_artists SET isFavorite = NOT isFavorite WHERE mbid = :mbid")
    suspend fun toggleFavorite(mbid: String)
    
    /**
     * Atualiza status de sincronização
     */
    @Query("""
        UPDATE metal_artists 
        SET syncStatus = :status,
            lastSyncAttempt = :timestamp,
            syncAttempts = syncAttempts + 1
        WHERE mbid = :mbid
    """)
    suspend fun updateSyncStatus(mbid: String, status: SyncStatus, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Remove artista por MBID
     */
    @Query("DELETE FROM metal_artists WHERE mbid = :mbid")
    suspend fun deleteArtist(mbid: String)
    
    /**
     * Remove artistas expirados
     */
    @Query("DELETE FROM metal_artists WHERE lastUpdated < :expirationTime AND isFavorite = 0")
    suspend fun deleteExpired(expirationTime: Long): Int
    
    /**
     * Limpa todos os artistas (exceto favoritos)
     */
    @Query("DELETE FROM metal_artists WHERE isFavorite = 0")
    suspend fun clearAll(): Int
    
    /**
     * Limpa cache completamente
     */
    @Query("DELETE FROM metal_artists")
    suspend fun clearAllComplete()
}

/**
 * Resultado de contagem por país
 */
data class CountryCount(
    val country: String,
    val count: Int
)