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
 * Repositório de Descoberta Dinâmica de Metal
 * 
 * Implementa descoberta musical baseada EXCLUSIVAMENTE na biblioteca do usuário,
 * sem hardcoded de gêneros. O sistema analiza os dados reais e detecta padrões
 * automaticamente usando:
 * - Tags do MusicBrainz
 * - Frequência de artistas
 * - Relations entre artistas
 * - Scores dinâmicos de similaridade
 */
@Singleton
class DynamicMetalDiscoveryRepository @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val musicBrainzService: MusicBrainzService,
    private val coverArtService: CoverArtArchiveService,
    private val dynamicDiscovery: DynamicMusicDiscovery,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver
) {

    /**
     * Executa a descoberta musical dinâmica completa
     * 1. Analisa a biblioteca real do usuário
     * 2. Busca tags no MusicBrainz
     * 3. Detecta estilos automaticamente
     * 4. Recomenda bandas baseadas nos padrões descobertos
     */
    suspend fun performDynamicDiscovery(): DiscoveryResult = withContext(Dispatchers.IO) {
        // Obter artistas únicos da biblioteca
        val allItems = downloadRepository.stream().first()
        val uniqueArtists = allItems
            .mapNotNull { it.artist?.toString()?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        // Executar descoberta dinâmica
        dynamicDiscovery.analyzeLibraryAndDiscover(uniqueArtists)
    }

    /**
     * Descobre artistas similares para uma banda específica
     */
    suspend fun getSimilarArtists(bandName: String): List<DiscoveredArtist> = withContext(Dispatchers.IO) {
        val similar = musicBrainzService.discoverSimilarBands(bandName)
        
        similar.bands.map { band ->
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
    }

    /**
     * Obtém discografia de uma banda com capas reais
     */
    suspend fun getBandDiscography(bandName: String): DynamicBandInfo = withContext(Dispatchers.IO) {
        val mbid = musicBrainzService.searchArtistId(bandName)
            ?: return@withContext DynamicBandInfo.empty()

        // Buscar detalhes
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
            
            DynamicAlbum(
                id = rg.id,
                title = rg.title,
                year = rg.firstReleaseDate.take(4),
                type = rg.primaryType,
                coverUrl = coverUrl
            )
        }

        // Buscar similares
        val similarBands = getSimilarArtists(bandName)

        DynamicBandInfo(
            name = bandName,
            mbid = mbid,
            country = details?.country,
            tags = details?.tags ?: details?.genres ?: emptyList(),
            genres = details?.genres ?: emptyList(),
            aliases = details?.aliases ?: emptyList(),
            disambiguation = details?.disambiguation,
            albums = albumsWithCovers.take(30),
            similarArtists = similarBands.take(10),
            isActive = details?.let { 
                it.tags.isNotEmpty() || it.genres.isNotEmpty() 
            } ?: true
        )
    }

    /**
     * Baixa as melhores músicas de uma banda
     */
    suspend fun downloadBandBestSongs(bandName: String): MetalDownloadResult = withContext(Dispatchers.IO) {
        try {
            val query = "ytsearch1:\"$bandName best songs\""
            
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
            
            MetalDownloadResult.Success("Download agendado: $bandName")
        } catch (e: Exception) {
            MetalDownloadResult.Error("Erro: ${e.message}")
        }
    }

    /**
     * Baixa um álbum específico
     */
    suspend fun downloadAlbum(
        bandName: String,
        albumName: String,
        year: String? = null
    ): MetalDownloadResult = withContext(Dispatchers.IO) {
        try {
            val query = when {
                year != null && year.length == 4 -> 
                    "ytsearch1:\"$bandName $albumName $year full album\""
                else -> 
                    "ytsearch1:\"$bandName $albumName full album\""
            }
            
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
            
            MetalDownloadResult.Success("Download agendado: $bandName - $albumName")
        } catch (e: Exception) {
            MetalDownloadResult.Error("Erro: ${e.message}")
        }
    }

    /**
     * Baixa todos os álbuns de uma banda
     */
    suspend fun downloadAllAlbums(bandName: String): List<MetalDownloadResult> = withContext(Dispatchers.IO) {
        val discography = getBandDiscography(bandName)
        val results = mutableListOf<MetalDownloadResult>()

        discography.albums.take(5).forEachIndexed { index, album ->
            val result = downloadAlbum(bandName, album.title, album.year)
            results.add(result)
            
            // Delay entre downloads para evitar sobrecarga
            if (index < discography.albums.size - 1) {
                kotlinx.coroutines.delay(2000)
            }
        }

        results
    }
}

/**
 * Informações completas de uma banda
 */
data class DynamicBandInfo(
    val name: String,
    val mbid: String,
    val country: String?,
    val tags: List<String>,
    val genres: List<String>,
    val aliases: List<String>,
    val disambiguation: String?,
    val albums: List<DynamicAlbum>,
    val similarArtists: List<DiscoveredArtist>,
    val isActive: Boolean
) {
    companion object {
        fun empty() = DynamicBandInfo(
            name = "",
            mbid = "",
            country = null,
            tags = emptyList(),
            genres = emptyList(),
            aliases = emptyList(),
            disambiguation = null,
            albums = emptyList(),
            similarArtists = emptyList(),
            isActive = false
        )
    }
}

/**
 * Álbum com capa
 */
data class DynamicAlbum(
    val id: String,
    val title: String,
    val year: String,
    val type: String,
    val coverUrl: String?
)

/**
 * Resultado do download (versão alternativa para evitar conflito)
 */
sealed class MetalDownloadResult {
    data class Success(val message: String) : MetalDownloadResult()
    data class Error(val message: String) : MetalDownloadResult()
}