package com.example.ytdown.data.local.metal.dao

import androidx.room.*
import com.example.ytdown.data.local.metal.entities.ArtistStatsDto
import com.example.ytdown.data.local.metal.entities.InteractionType
import com.example.ytdown.data.local.metal.entities.ListeningHistoryEntity
import com.example.ytdown.data.local.metal.entities.ListeningStats
import kotlinx.coroutines.flow.Flow

/**
 * DAO completo para Histórico de Escuta do sistema Metal
 * 
 * Suporta:
 * - Registro de playback
 * - Estatísticas por artista/álbum
 * - Análise de padrões
 * - Agregações para perfil musical
 */
@Dao
interface ListeningHistoryDao {

    // =====================================================
    // INSERT
    // =====================================================
    
    /**
     * Registra um evento de escuta
     */
    @Insert
    suspend fun insertListeningEvent(event: ListeningHistoryEntity): Long
    
    /**
     * Registra múltiplos eventos em batch
     */
    @Insert
    suspend fun insertListeningEvents(events: List<ListeningHistoryEntity>)
    
    /**
     * Registra início de reprodução
     */
    suspend fun registerPlaybackStart(
        artistName: String,
        artistMbid: String? = null,
        albumName: String? = null,
        albumMbid: String? = null,
        trackName: String? = null,
        genre: String? = null,
        durationMs: Long? = null
    ): Long {
        val event = ListeningHistoryEntity.create(
            artistName = artistName,
            albumName = albumName,
            trackName = trackName,
            genre = genre,
            durationMs = durationMs,
            interactionType = InteractionType.PLAYED
        ).copy(
            artistMbid = artistMbid,
            albumMbid = albumMbid
        )
        return insertListeningEvent(event)
    }
    
    /**
     * Registra conclusão de reprodução
     */
    @Query("""
        UPDATE listening_history
        SET listenedDurationMs = :durationMs,
            completed = :completed,
            listenedPercentage = 1.0
        WHERE id = :eventId
    """)
    suspend fun registerPlaybackComplete(
        eventId: Long,
        durationMs: Long,
        completed: Boolean = true
    )
    
    /**
     * Registra skip
     */
    @Query("""
        UPDATE listening_history
        SET interactionType = 'SKIPPED',
            listenedDurationMs = 0
        WHERE id = :eventId
    """)
    suspend fun registerSkip(eventId: Long)
    
    /**
     * Registra favorito
     */
    @Query("""
        UPDATE listening_history
        SET interactionType = 'FAVORITED'
        WHERE id = :eventId
    """)
    suspend fun registerFavorite(eventId: Long)
    
    // =====================================================
    // QUERIES
    // =====================================================
    
    /**
     * Histórico recente
     */
    @Query("""
        SELECT * FROM listening_history
        ORDER BY listenedAt DESC
        LIMIT :limit
    """)
    fun observeRecentHistory(limit: Int = 50): Flow<List<ListeningHistoryEntity>>
    
    /**
     * Histórico por artista
     */
    @Query("""
        SELECT * FROM listening_history
        WHERE artistName = :artistName
        ORDER BY listenedAt DESC
        LIMIT :limit
    """)
    fun observeHistoryByArtist(artistName: String, limit: Int = 20): Flow<List<ListeningHistoryEntity>>
    
    /**
     * Histórico por gênero
     */
    @Query("""
        SELECT * FROM listening_history
        WHERE genre = :genre
        ORDER BY listenedAt DESC
        LIMIT :limit
    """)
    fun observeHistoryByGenre(genre: String, limit: Int = 20): Flow<List<ListeningHistoryEntity>>
    
    /**
     * Histórico de hoje
     */
    @Query("""
        SELECT * FROM listening_history
        WHERE dateString = :dateString
        ORDER BY listenedAt DESC
    """)
    fun observeTodayHistory(dateString: String): Flow<List<ListeningHistoryEntity>>
    
    /**
     * Histórico da última semana
     */
    @Query("""
        SELECT * FROM listening_history
        WHERE listenedAt > :sinceTimestamp
        ORDER BY listenedAt DESC
    """)
    fun observeWeekHistory(sinceTimestamp: Long): Flow<List<ListeningHistoryEntity>>
    
    // =====================================================
    // ESTATÍSTICAS
    // =====================================================
    
    /**
     * Total de plays por artista
     */
    @Query("""
        SELECT artistName, 
               COUNT(*) as totalPlays,
               SUM(listenedDurationMs) as totalListenTimeMs,
               AVG(listenedDurationMs) as avgListenDurationMs,
               AVG(CASE WHEN completed = 1 THEN 1.0 ELSE 0.0 END) as completionRate,
               AVG(CASE WHEN interactionType = 'SKIPPED' THEN 1.0 ELSE 0.0 END) as skipRate,
               MAX(listenedAt) as lastPlayedAt,
               MIN(listenedAt) as firstPlayedAt
        FROM listening_history
        WHERE interactionType IN ('PLAYED', 'COMPLETED', 'SKIPPED')
        GROUP BY artistName
        ORDER BY totalPlays DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(limit: Int = 20): List<ArtistListeningStats>
    
    /**
     * Plays por gênero
     */
    @Query("""
        SELECT genre,
               COUNT(*) as playCount,
               SUM(listenedDurationMs) as totalTimeMs
        FROM listening_history
        WHERE genre IS NOT NULL AND genre != ''
        GROUP BY genre
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getGenreStats(limit: Int = 10): List<GenreListeningStats>
    
    /**
     * Plays por país (artista) - usando LEFT JOIN para evitar subquery no FROM
     */
    @Query("""
        SELECT 
            lh.artistName as artistName,
            COALESCE(ma.country, 'Unknown') as country,
            COUNT(*) as count
        FROM listening_history lh
        LEFT JOIN metal_artists ma ON lh.artistName = ma.name
        WHERE ma.country IS NOT NULL
        GROUP BY lh.artistName, ma.country
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getCountryStats(limit: Int = 10): List<CountryListeningStats>
    
    /**
     * Plays por hora do dia
     */
    @Query("""
        SELECT hourOfDay, COUNT(*) as playCount
        FROM listening_history
        GROUP BY hourOfDay
        ORDER BY playCount DESC
    """)
    suspend fun getHourlyStats(): List<HourlyStats>
    
    /**
     * Plays por dia da semana
     */
    @Query("""
        SELECT dayOfWeek, COUNT(*) as playCount
        FROM listening_history
        GROUP BY dayOfWeek
        ORDER BY dayOfWeek
    """)
    suspend fun getDailyStats(): List<DailyStats>
    
    /**
     * Evolução semanal (plays por dia)
     */
    @Query("""
        SELECT dateString, COUNT(*) as playCount, SUM(listenedDurationMs) as totalTimeMs
        FROM listening_history
        WHERE listenedAt > :sinceTimestamp
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    suspend fun getWeeklyEvolution(sinceTimestamp: Long): List<DailyEvolution>
    
    /**
     * Estatísticas completas de artista - retorna DTO específico do Room
     */
    @Query("""
        SELECT artistName,
               COUNT(*) as totalPlays,
               SUM(listenedDurationMs) as totalListenTimeMs,
               AVG(listenedDurationMs) as avgListenDurationMs,
               AVG(CASE WHEN completed = 1 THEN 1.0 ELSE 0.0 END) as completionRate,
               AVG(CASE WHEN interactionType = 'SKIPPED' THEN 1.0 ELSE 0.0 END) as skipRate,
               MAX(listenedAt) as lastPlayedAt
        FROM listening_history
        WHERE artistName = :artistName
        GROUP BY artistName
    """)
    suspend fun getArtistStats(artistName: String): ArtistStatsDto?
    
    /**
     * Totais globais
     */
    @Query("""
        SELECT 
            COUNT(*) as totalEvents,
            SUM(CASE WHEN interactionType IN ('PLAYED', 'COMPLETED') THEN 1 ELSE 0 END) as totalPlays,
            SUM(listenedDurationMs) as totalListenTimeMs,
            COUNT(DISTINCT artistName) as uniqueArtists,
            COUNT(DISTINCT albumName) as uniqueAlbums
        FROM listening_history
    """)
    suspend fun getGlobalStats(): GlobalListeningStats
    
    // =====================================================
    // DELETE/CLEANUP
    // =====================================================
    
    /**
     * Remove registros antigos (manter apenas últimos 90 dias)
     */
    @Query("DELETE FROM listening_history WHERE listenedAt < :timestamp")
    suspend fun deleteOldRecords(timestamp: Long): Int
    
    /**
     * Limpa histórico completamente
     */
    @Query("DELETE FROM listening_history")
    suspend fun clearAll()
    
    /**
     * Remove eventos específicos
     */
    @Delete
    suspend fun deleteEvent(event: ListeningHistoryEntity)
}

/**
 * Resultado de estatísticas por artista
 */
data class ArtistListeningStats(
    val artistName: String,
    val totalPlays: Int,
    val totalListenTimeMs: Long,
    val avgListenDurationMs: Long,
    val completionRate: Double,
    val skipRate: Double,
    val lastPlayedAt: Long,
    val firstPlayedAt: Long
)

data class GenreListeningStats(
    val genre: String,
    val playCount: Int,
    val totalTimeMs: Long
)

data class CountryListeningStats(
    val artistName: String,
    val country: String,
    val count: Int
)

data class HourlyStats(
    val hourOfDay: Int,
    val playCount: Int
)

data class DailyStats(
    val dayOfWeek: Int,
    val playCount: Int
)

data class DailyEvolution(
    val dateString: String,
    val playCount: Int,
    val totalTimeMs: Long
)

data class GlobalListeningStats(
    val totalEvents: Int,
    val totalPlays: Int,
    val totalListenTimeMs: Long,
    val uniqueArtists: Int,
    val uniqueAlbums: Int
)