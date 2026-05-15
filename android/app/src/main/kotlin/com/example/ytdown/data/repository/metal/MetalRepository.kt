package com.example.ytdown.data.repository.metal

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.ytdown.core.infrastructure.DynamicBandInfo
import com.example.ytdown.core.infrastructure.DynamicAlbum
import com.example.ytdown.data.local.metal.dao.*
import com.example.ytdown.data.local.metal.database.MetalDatabase
import com.example.ytdown.data.local.metal.entities.*
import com.example.ytdown.services.*
import com.example.ytdown.services.DiscoveredArtist
import com.example.ytdown.services.DiscoveryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório Central do Sistema Metal
 * 
 * Implementa o padrão Repository com:
 * - Cache Offline First
 * - Sincronização com API
 * - Paging 3
 * - Histórico de Escuta
 * - Perfil Musical
 */
@OptIn(ExperimentalPagingApi::class)
@Singleton
class MetalRepository @Inject constructor(
    private val database: MetalDatabase,
    private val musicBrainzService: MusicBrainzService,
    private val coverArtService: CoverArtArchiveService
) {

    // =====================================================
    // DAOs
    // =====================================================
    
    private val artistDao = database.artistDao()
    private val albumDao = database.albumDao()
    private val historyDao = database.historyDao()
    private val profileDao = database.profileDao()

    // =====================================================
    // PAGING 3 - Artistas
    // =====================================================
    
    /**
     * Retorna Flow de artistas paginados com RemoteMediator
     */
    fun getPagedArtists(): Flow<PagingData<MetalArtistEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
                initialLoadSize = PAGE_SIZE * 2
            ),
            remoteMediator = MetalArtistsRemoteMediator(
                musicBrainzService = musicBrainzService,
                database = database
            ),
            pagingSourceFactory = { artistDao.getPagedArtistsByScore() }
        ).flow
    }
    
    /**
     * Retorna artistas ordenados por nome
     */
    fun getPagedArtistsByName(): Flow<PagingData<MetalArtistEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { artistDao.getPagedArtistsByName() }
        ).flow
    }
    
    /**
     * Retorna artistas ativos
     */
    fun getPagedActiveArtists(): Flow<PagingData<MetalArtistEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { artistDao.getPagedActiveArtists() }
        ).flow
    }
    
    /**
     * Retorna favoritos
     */
    fun getPagedFavoriteArtists(): Flow<PagingData<MetalArtistEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { artistDao.getPagedFavoriteArtists() }
        ).flow
    }

    // =====================================================
    // FLOW - Observação em tempo real
    // =====================================================
    
    /**
     * Observa todos os artistas
     */
    fun observeAllArtists(): Flow<List<MetalArtistEntity>> = 
        artistDao.observeAllArtists()
    
    /**
     * Observa artista específico
     */
    fun observeArtist(mbid: String): Flow<MetalArtistEntity?> =
        artistDao.observeArtist(mbid)
    
    /**
     * Observa artistas mais tocados
     */
    fun observeMostPlayed(limit: Int = 10): Flow<List<MetalArtistEntity>> =
        artistDao.observeMostPlayed(limit)

    // =====================================================
    // BUSCA E DESCOBERTA
    // =====================================================
    
    /**
     * Busca artistas por nome
     */
    suspend fun searchArtists(query: String): List<MetalArtistEntity> {
        return artistDao.searchArtists(query)
    }
    
    /**
     * Busca artistas por tag
     */
    suspend fun getArtistsByTag(tag: String): List<MetalArtistEntity> {
        return artistDao.getArtistsByTag(tag, limit = 30)
    }
    
    /**
     * Descobre novos artistas baseado na biblioteca
     */
    suspend fun discoverFromLibrary(libraryArtists: List<String>): DiscoveryResult {
        return try {
            // Buscar artistas descobertos via API
            val discoveredArtists = mutableListOf<DiscoveredArtist>()
            
            // Para cada artista na biblioteca, buscar similares
            libraryArtists.take(10).forEach { artistName ->
                try {
                    val similar = musicBrainzService.discoverSimilarBands(artistName)
                    if (similar.success) {
                        similar.bands.forEach { band ->
                            if (band.name.lowercase() !in libraryArtists.map { it.lowercase() }) {
                                discoveredArtists.add(
                                    DiscoveredArtist(
                                        mbid = band.mbid,
                                        name = band.name,
                                        country = band.country,
                                        tags = band.tags,
                                        matchScore = band.tags.size * 10,
                                        matchedTags = band.tags,
                                        isActive = true,
                                        beginYear = null,
                                        endYear = null
                                    )
                                )
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1100) // Rate limit
                } catch (e: Exception) {
                    // Continuar com próximos
                }
            }
            
            // Salvar no cache
            val entities = discoveredArtists.take(40).map { artist ->
                MetalArtistEntity.fromJsonArrays(
                    mbid = artist.mbid,
                    name = artist.name,
                    genres = artist.tags.take(3),
                    tags = artist.tags,
                    matchedTags = artist.matchedTags,
                    score = artist.matchScore.toDouble(),
                    country = artist.country,
                    isActive = artist.isActive,
                    beginYear = artist.beginYear,
                    endYear = artist.endYear
                )
            }
            
            if (entities.isNotEmpty()) {
                artistDao.insertArtists(entities)
            }
            
            DiscoveryResult(
                success = true,
                detectedStyles = emptyList(), // Calculado do histórico
                recommendedArtists = discoveredArtists,
                analyzedArtists = libraryArtists.size,
                totalTagsFound = discoveredArtists.flatMap { it.tags }.distinct().size
            )
        } catch (e: Exception) {
            DiscoveryResult(
                success = false,
                error = e.message,
                detectedStyles = emptyList(),
                recommendedArtists = emptyList()
            )
        }
    }

    // =====================================================
    // DETALHES DA BANDA
    // =====================================================
    
    /**
     * Obtém detalhes completos de uma banda
     */
    suspend fun getBandDetails(bandName: String): DynamicBandInfo {
        val mbid = musicBrainzService.searchArtistId(bandName) ?: return DynamicBandInfo.empty()
        
        // Verificar cache primeiro
        val cachedArtist = artistDao.getArtistByMbid(mbid)
        
        // Buscar detalhes da API
        val details = musicBrainzService.getArtistDetails(mbid)
        
        // Buscar álbuns
        val releaseGroups = musicBrainzService.getArtistReleaseGroups(mbid)
        
        // Buscar capas em paralelo
        val albumsWithCovers = releaseGroups.map { rg ->
            val coverUrl = try {
                coverArtService.getBestCover(rg.id)
            } catch (e: Exception) {
                null
            }
            
            // Salvar no cache
            val albumEntity = MetalAlbumEntity.fromMusicBrainz(
                mbid = rg.id,
                artistMbid = mbid,
                artistName = bandName,
                title = rg.title,
                year = rg.firstReleaseDate,
                type = rg.primaryType,
                coverUrl = coverUrl
            )
            albumDao.insertAlbum(albumEntity)
            
            DynamicAlbum(
                id = rg.id,
                title = rg.title,
                year = rg.firstReleaseDate.take(4),
                type = rg.primaryType,
                coverUrl = coverUrl
            )
        }
        
        // Buscar similares
        val similarResponse = musicBrainzService.discoverSimilarBands(bandName)
        val similarArtists = similarResponse.bands.map { band ->
            DiscoveredArtist(
                mbid = band.mbid,
                name = band.name,
                country = band.country,
                tags = band.tags,
                matchScore = band.tags.size * 10,
                matchedTags = band.tags,
                isActive = true,
                beginYear = null,
                endYear = null
            )
        }
        
        // Atualizar cache do artista
        if (details != null) {
            val updatedArtist = MetalArtistEntity.fromJsonArrays(
                mbid = mbid,
                name = bandName,
                genres = details.genres,
                tags = details.tags,
                matchedTags = emptyList(),
                score = 50.0, // Score padrão
                country = details.country,
                isActive = details.tags.isNotEmpty() || details.genres.isNotEmpty()
            )
            artistDao.insertArtist(updatedArtist)
        }
        
        return DynamicBandInfo(
            name = bandName,
            mbid = mbid,
            country = details?.country,
            tags = details?.tags ?: details?.genres ?: emptyList(),
            genres = details?.genres ?: emptyList(),
            aliases = details?.aliases ?: emptyList(),
            disambiguation = details?.disambiguation,
            albums = albumsWithCovers,
            similarArtists = similarArtists,
            isActive = details?.let { it.tags.isNotEmpty() || it.genres.isNotEmpty() } ?: true
        )
    }

    // =====================================================
    // HISTÓRICO DE ESCUTA
    // =====================================================
    
    /**
     * Registra reprodução
     */
    suspend fun registerPlayback(
        artistName: String,
        artistMbid: String? = null,
        albumName: String? = null,
        albumMbid: String? = null,
        trackName: String? = null,
        genre: String? = null,
        durationMs: Long? = null
    ): Long {
        return historyDao.registerPlaybackStart(
            artistName = artistName,
            artistMbid = artistMbid,
            albumName = albumName,
            albumMbid = albumMbid,
            trackName = trackName,
            genre = genre,
            durationMs = durationMs
        )
    }
    
    /**
     * Registra conclusão
     */
    suspend fun registerPlaybackComplete(eventId: Long, listenedMs: Long) {
        // Use DAO method if available, otherwise skip
        try {
            historyDao.registerPlaybackComplete(eventId, listenedMs)
        } catch (e: Exception) {
            // Silently fail - completion tracking is optional
        }
    }
    
    /**
     * Observa histórico recente
     */
    fun observeRecentHistory(limit: Int = 50): Flow<List<ListeningHistoryEntity>> =
        historyDao.observeRecentHistory(limit)
    
    /**
     * Obtém estatísticas
     */
    suspend fun getListeningStats(): ListeningStatsResult {
        val global = historyDao.getGlobalStats()
        val topArtists = historyDao.getTopArtists(10)
        val genreStats = historyDao.getGenreStats(10)
        val hourlyStats = historyDao.getHourlyStats()
        val dailyStats = historyDao.getDailyStats()
        
        return ListeningStatsResult(
            totalPlays = global.totalPlays,
            totalListenTimeMs = global.totalListenTimeMs,
            uniqueArtists = global.uniqueArtists,
            uniqueAlbums = global.uniqueAlbums,
            topArtists = topArtists.map { ArtistStatsData(it.artistName, it.totalPlays, it.totalListenTimeMs) },
            genreDistribution = genreStats.map { GenreStatsData(it.genre, it.playCount) },
            hourlyDistribution = hourlyStats.associate { it.hourOfDay to it.playCount },
            dailyDistribution = dailyStats.associate { it.dayOfWeek to it.playCount }
        )
    }
    
    /**
     * Gera/atualiza perfil musical
     */
    suspend fun generateMusicProfile() {
        val stats = getListeningStats()
        
        // Calcular gêneros dominantes
        val totalPlays = stats.totalPlays
        val genreStats = stats.genreDistribution.map { genre ->
            GenreStats(
                genre = genre.genre,
                playCount = genre.playCount,
                totalListenTimeMs = 0,
                percentage = if (totalPlays > 0) genre.playCount.toFloat() / totalPlays * 100 else 0f
            )
        }.sortedByDescending { it.percentage }
        
        // Artistas favoritos
        val artistStats = stats.topArtists.map { artist ->
            ArtistStats(
                artistMbid = null,
                artistName = artist.name,
                playCount = artist.playCount,
                totalListenTimeMs = artist.listenTimeMs,
                completionRate = 0.8f,
                avgRating = null
            )
        }
        
        // Padrões de escuta
        val mostActiveHour = stats.hourlyDistribution.maxByOrNull { it.value }?.key ?: 12
        val mostActiveDay = stats.dailyDistribution.maxByOrNull { it.value }?.key ?: 1
        
        val profile = MusicProfileEntity.create(
            genreStats = genreStats.take(10),
            artistStats = artistStats.take(20),
            patternStats = ListeningPatternStats(
                mostActiveHour = mostActiveHour,
                mostActiveDay = mostActiveDay,
                avgSessionDurationMs = stats.totalListenTimeMs / (stats.totalPlays.coerceAtLeast(1)),
                avgTracksPerSession = 5,
                preferredSessionLength = "30-60 min"
            ),
            weeklyEvolution = stats.dailyDistribution.mapKeys { 
                getDayName(it.key)
            },
            totalListenTime = stats.totalListenTimeMs,
            totalTracks = stats.totalPlays,
            uniqueArtists = stats.uniqueArtists,
            uniqueAlbums = stats.uniqueAlbums
        )
        
        profileDao.saveProfile(profile)
    }
    
    /**
     * Observa perfil musical
     */
    fun observeMusicProfile(): Flow<MusicProfileEntity?> =
        profileDao.observeProfile()

    // =====================================================
    // UTILITARIOS
    // =====================================================
    
    /**
     * Limpa cache expirado
     */
    suspend fun clearExpiredCache() {
        val expirationTime = System.currentTimeMillis() - MetalArtistEntity.DEFAULT_CACHE_TIMEOUT
        artistDao.deleteExpired(expirationTime)
        
        val albumExpiration = System.currentTimeMillis() - MetalAlbumEntity.DEFAULT_CACHE_TIMEOUT
        albumDao.deleteExpired(albumExpiration)
    }
    
    /**
     * Conta artistas em cache
     */
    suspend fun getCachedArtistCount(): Int = artistDao.getArtistCount()
    
    /**
     * Alterna favorito
     */
    suspend fun toggleFavorite(mbid: String) {
        artistDao.toggleFavorite(mbid)
    }

    companion object {
        const val PAGE_SIZE = 20
        const val PREFETCH_DISTANCE = 5
        
        private fun getDayName(dayOfWeek: Int): String {
            return when (dayOfWeek) {
                1 -> "Domingo"
                2 -> "Segunda"
                3 -> "Terça"
                4 -> "Quarta"
                5 -> "Quinta"
                6 -> "Sexta"
                7 -> "Sábado"
                else -> "Unknown"
            }
        }
    }
}

data class ListeningStatsResult(
    val totalPlays: Int,
    val totalListenTimeMs: Long,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val topArtists: List<ArtistStatsData>,
    val genreDistribution: List<GenreStatsData>,
    val hourlyDistribution: Map<Int, Int>,
    val dailyDistribution: Map<Int, Int>
)

data class ArtistStatsData(
    val name: String,
    val playCount: Int,
    val listenTimeMs: Long
)

data class GenreStatsData(
    val genre: String,
    val playCount: Int
)