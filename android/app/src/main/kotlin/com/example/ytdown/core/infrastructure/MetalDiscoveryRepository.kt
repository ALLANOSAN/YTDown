package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.services.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de Descoberta de Metal - Integração completa de todos os serviços
 * 
 * Este repositório encapsula toda a lógica de:
 * - Análise do perfil musical do usuário
 * - Busca de bandas no MusicBrainz
 * - Integração com Cover Art Archive
 * - Sistema de recomendação local
 * - Download de álbuns
 * - Cache offline
 */
@Singleton
class MetalDiscoveryRepository @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val musicBrainzService: MusicBrainzService,
    private val coverArtService: CoverArtArchiveService,
    private val recommendationEngine: MetalRecommendationEngine,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver
) {

    // =====================================================
    // ANÁLISE DO PERFIL DO USUÁRIO
    // =====================================================

    /**
     * Analisa a biblioteca do usuário e cria um perfil musical
     * @return Perfil musical com gêneros, tags e bandas favoritas
     */
    suspend fun analyzeUserProfile(): UserMetalProfile = withContext(Dispatchers.IO) {
        val libraryItems = downloadRepository.stream().first()
        recommendationEngine.analyzeUserProfile(libraryItems)
    }

    /**
     * Versão com fallback para bibliotecas vazias
     */
    suspend fun analyzeUserProfileWithFallback(): UserMetalProfile = withContext(Dispatchers.IO) {
        val profile = analyzeUserProfile()
        
        // Se o perfil não está bem definido, retorna perfil padrão para novos usuários
        if (!profile.isWellDefined()) {
            return@withContext UserMetalProfile(
                favoriteGenres = listOf("power metal", "heavy metal", "thrash metal"),
                favoriteTags = listOf("power metal", "heavy metal", "thrash metal", "speed metal"),
                favoriteBands = listOf("Iron Maiden", "Metallica", "Black Sabbath", "Slayer", "Pantera"),
                favoriteCountries = listOf("US", "GB", "SE"),
                totalTracks = 0,
                analysisTimestamp = System.currentTimeMillis()
            )
        }
        
        profile
    }

    // =====================================================
    // DESCOBERTA DE BANDAS
    // =====================================================

    /**
     * Descobre bandas similares baseadas no perfil do usuário
     * @param userProfile Perfil musical do usuário
     * @param excludeExistingBandNames Nomes de bandas já na biblioteca
     * @return Lista de bandas recomendadas com scores
     */
    suspend fun discoverSimilarBands(
        userProfile: UserMetalProfile,
        excludeExistingBandNames: Set<String> = emptySet()
    ): List<RecommendedBand> = withContext(Dispatchers.IO) {
        
        val searchTags = userProfile.getTopSearchTags().ifEmpty { 
            listOf("power metal", "heavy metal", "black metal") 
        }
        
        val allCandidates = mutableListOf<Pair<MBArtist, SimilarityResult>>()
        val seenBandNames = mutableSetOf<String>()
        
        // Buscar por cada tag do perfil
        for (tag in searchTags) {
            try {
                val artists = musicBrainzService.searchArtistsByTag(tag, limit = 25)
                
                artists.forEach { artist ->
                    // Evitar duplicatas
                    val artistNameLower = artist.name.lowercase()
                    if (artistNameLower in seenBandNames) return@forEach
                    
                    // Não incluir bandas já existentes na biblioteca
                    if (excludeExistingBandNames.any { existing -> 
                        artistNameLower.contains(existing.lowercase()) || 
                        existing.lowercase().contains(artistNameLower) 
                    }) return@forEach
                    
                    seenBandNames.add(artistNameLower)
                    
                    // Calcular similaridade
                    val similarity = recommendationEngine.calculateSimilarityScore(
                        artistTags = artist.tags,
                        artistGenre = artist.tags.firstOrNull(),
                        artistCountry = artist.country,
                        userProfile = userProfile
                    )
                    
                    // Filtrar apenas recomendações de qualidade mínima
                    if (similarity.score > 10) {
                        allCandidates.add(artist to similarity)
                    }
                }
                
                // Pequeno delay para evitar rate limit
                kotlinx.coroutines.delay(100)
                
            } catch (e: Exception) {
                // Continuar mesmo se uma busca falhar
                continue
            }
        }
        
        // Ordenar por score e retornar top 40
        allCandidates
            .sortedByDescending { it.second.score }
            .take(40)
            .map { (artist, similarity) ->
                RecommendedBand(
                    mbid = artist.id,
                    name = artist.name,
                    country = artist.country,
                    genre = artist.tags.firstOrNull() ?: similarity.matchReasons.firstOrNull(),
                    tags = artist.tags,
                    similarityScore = similarity.score,
                    matchedTags = similarity.matchedTags,
                    matchLevel = similarity.getMatchLevel(),
                    isActive = artist.lifeSpan?.ended != true,
                    beginYear = artist.lifeSpan?.begin,
                    endYear = if (artist.lifeSpan?.ended == true) artist.lifeSpan?.end else null
                )
            }
    }

    // =====================================================
    // DETALHES DA BANDA
    // =====================================================

    /**
     * Obtém detalhes completos de uma banda
     * @param mbid MBID da banda
     * @return Detalhes completos ou null
     */
    suspend fun getBandDetails(mbid: String): MBBandDetails? = withContext(Dispatchers.IO) {
        musicBrainzService.getArtistDetails(mbid)
    }

    /**
     * Obtém detalhes pelo nome (busca MBID primeiro)
     */
    suspend fun getBandDetailsByName(name: String): MBBandDetails? = withContext(Dispatchers.IO) {
        val mbid = musicBrainzService.searchArtistId(name) ?: return@withContext null
        musicBrainzService.getArtistDetails(mbid)
    }

    /**
     * Obtém discografia completa de uma banda
     * @param bandName Nome da banda
     * @return Lista de álbuns com informações completas
     */
    suspend fun getBandDiscography(bandName: String): BandDiscography = withContext(Dispatchers.IO) {
        val mbid = musicBrainzService.searchArtistId(bandName) 
            ?: return@withContext BandDiscography(emptyList(), emptyList(), null)
        
        // Buscar release groups (álbuns)
        val releaseGroups = musicBrainzService.getArtistReleaseGroups(mbid)
        
        // Para cada release group, tentar buscar a capa
        val albumsWithCovers = releaseGroups.map { rg ->
            val coverUrl = coverArtService.getBestCover(rg.id)
            AlbumWithCover(
                releaseGroupId = rg.id,
                title = rg.title,
                year = rg.firstReleaseDate.take(4),
                primaryType = rg.primaryType,
                artistCredit = rg.artistCredit,
                coverUrl = coverUrl
            )
        }
        
        // Buscar artista para informações adicionais
        val artistDetails = musicBrainzService.getArtistDetails(mbid)
        val similarBands = if (artistDetails != null) {
            val similar = musicBrainzService.discoverSimilarBands(bandName)
            similar.bands.map { it.name }
        } else {
            emptyList()
        }
        
        BandDiscography(
            albums = albumsWithCovers,
            similarBandNames = similarBands,
            artistDetails = artistDetails
        )
    }

    // =====================================================
    // CAPAS DOS ÁLBUNS
    // =====================================================

    /**
     * Busca capa de um álbum específico
     */
    suspend fun getAlbumCover(releaseGroupMBID: String): String? = withContext(Dispatchers.IO) {
        coverArtService.getBestCover(releaseGroupMBID)
    }

    /**
     * Busca capas para múltiplos álbuns em paralelo
     */
    suspend fun getAlbumCovers(releaseGroupMBIDs: List<String>): Map<String, String?> = withContext(Dispatchers.IO) {
        releaseGroupMBIDs.associateWith { mbid ->
            try {
                coverArtService.getBestCover(mbid)
            } catch (e: Exception) {
                null
            }
        }
    }

    // =====================================================
    // DOWNLOAD DE ÁLBUNS
    // =====================================================

    /**
     * Baixa um álbum completo do YouTube
     * @param bandName Nome da banda
     * @param albumName Nome do álbum
     * @param year Ano do álbum (opcional)
     * @return Resultado do agendamento
     */
    suspend fun downloadAlbum(
        bandName: String,
        albumName: String,
        year: String? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // Construir query de busca otimizada
            val query = buildAlbumSearchQuery(bandName, albumName, year)
            
            // Agendar download
            scheduler.schedule(
                url = VideoUrl(query),
                path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                meta = MediaMetadata(
                    MediaTitle(albumName),
                    ArtistName(bandName),
                    AlbumName(albumName)
                ),
                options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
            )
            
            DownloadResult.Success(
                message = "Download agendado: $bandName - $albumName",
                query = query
            )
        } catch (e: Exception) {
            DownloadResult.Error("Erro ao agendar download: ${e.message}")
        }
    }

    /**
     * Baixa as melhores músicas de uma banda
     */
    suspend fun downloadBandBestSongs(bandName: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val query = "ytsearch1:\"$bandName - Best Songs\""
            
            scheduler.schedule(
                url = VideoUrl(query),
                path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                meta = MediaMetadata(
                    MediaTitle("Best Songs"),
                    ArtistName(bandName),
                    AlbumName("Descoberta Metal")
                ),
                options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
            )
            
            DownloadResult.Success(
                message = "Download agendado: $bandName - Best Songs",
                query = query
            )
        } catch (e: Exception) {
            DownloadResult.Error("Erro ao agendar download: ${e.message}")
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun buildAlbumSearchQuery(bandName: String, albumName: String, year: String?): String {
        // Tentar diferentes formatos de busca para melhor resultado
        val base = "$bandName $albumName"
        val withYear = if (year != null && year.length == 4) "$bandName $albumName $year" else null
        val fullAlbum = "$base full album"
        val complete = "$base complete album"
        
        // Tentar primeiro com "full album" que geralmente retorna o álbum completo
        return when {
            withYear != null -> "ytsearch1:\"$withYear\""
            else -> "ytsearch1:\"$fullAlbum\""
        }
    }

    private fun detectGenreFromAlbum(albumName: String): String {
        val albumLower = albumName.lowercase()
        return when {
            albumLower.contains("best of") || albumLower.contains("greatest hits") || albumLower.contains("anthology") -> "Compilation"
            albumLower.contains("live") -> "Live Album"
            albumLower.contains("ep") -> "EP"
            albumLower.contains("single") -> "Single"
            albumLower.contains("demo") -> "Demo"
            albumLower.contains("compilation") -> "Compilation"
            else -> "Studio Album"
        }
    }
}

// =====================================================
// DATA MODELS
// =====================================================

/**
 * Banda recomendada com informações de similaridade
 */
data class RecommendedBand(
    val mbid: String,
    val name: String,
    val country: String?,
    val genre: String?,
    val tags: List<String>,
    val similarityScore: Int,
    val matchedTags: List<String>,
    val matchLevel: MatchLevel,
    val isActive: Boolean,
    val beginYear: String?,
    val endYear: String?
) {
    val activeYears: String?
        get() = when {
            beginYear != null && endYear != null -> "$beginYear - $endYear"
            beginYear != null && isActive -> "$beginYear - presente"
            else -> null
        }
}

/**
 * Álbum com informações de capa
 */
data class AlbumWithCover(
    val releaseGroupId: String,
    val title: String,
    val year: String,
    val primaryType: String,
    val artistCredit: String,
    val coverUrl: String?
)

/**
 * Discografia completa de uma banda
 */
data class BandDiscography(
    val albums: List<AlbumWithCover>,
    val similarBandNames: List<String>,
    val artistDetails: MBBandDetails?
)

/**
 * Resultado de uma operação de download
 */
sealed class DownloadResult {
    data class Success(val message: String, val query: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}