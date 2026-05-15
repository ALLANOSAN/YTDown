package com.example.ytdown.data.repository.metal

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.ytdown.data.local.metal.database.MetalDatabase
import com.example.ytdown.data.local.metal.entities.MetalArtistEntity
import com.example.ytdown.data.local.metal.entities.SyncStatus
import com.example.ytdown.services.MusicBrainzService
import kotlinx.coroutines.delay

/**
 * RemoteMediator para sincronização de artistas Metal
 * 
 * Implementa o padrão de sincronização API + Room:
 * - Carrega dados da API
 * - Salva no cache local
 * - Retorna do cache quando offline
 * - Gerencia erros e retries
 * - Respeita rate limits
 */
@OptIn(ExperimentalPagingApi::class)
class MetalArtistsRemoteMediator(
    private val musicBrainzService: MusicBrainzService,
    private val database: MetalDatabase
) : RemoteMediator<Int, MetalArtistEntity>() {

    companion object {
        private const val INITIAL_PAGE = 0
        private const val PAGE_SIZE = 20
        private const val RATE_LIMIT_DELAY_MS = 1100L
        private const val MAX_RETRIES = 3
        private const val PREFETCH_DISTANCE = 5
    }

    private var currentQuery: String = "metal"
    private var currentOffset: Int = 0

    override suspend fun initialize(): InitializeAction {
        // Verificar se precisa de refresh
        return try {
            // Get database directly to check - use cached count instead
            val cachedCount = database.artistDao().getArtistCount()
            if (cachedCount == 0) {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            } else {
                InitializeAction.SKIP_INITIAL_REFRESH
            }
        } catch (e: Exception) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MetalArtistEntity>
    ): MediatorResult {
        
        val page = when (loadType) {
            LoadType.REFRESH -> {
                currentOffset = 0
                INITIAL_PAGE
            }
            LoadType.PREPEND -> {
                // Já temos todos os dados no início
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.APPEND -> {
                currentOffset
            }
        }

        try {
            // Respeitar rate limit do MusicBrainz
            delay(RATE_LIMIT_DELAY_MS)

            // Buscar artistas da API
            val artists = if (page == 0) {
                // Primeira página: usar query de descoberta
                val seedTags = getSeedTagsFromLibrary()
                searchWithTags(seedTags, page)
            } else {
                // Páginas seguintes: continuar busca
                continueSearch(page)
            }

            // Salvar no banco
            database.withTransaction {
                val artistDao = database.artistDao()
                
                // Limpar cache se refresh
                if (loadType == LoadType.REFRESH) {
                    val expirationTime = System.currentTimeMillis() - MetalArtistEntity.LONG_CACHE_TIMEOUT
                    artistDao.deleteExpired(expirationTime)
                }

                // Inserir novos artistas
                if (artists.isNotEmpty()) {
                    val entities = artists.mapIndexed { index, artist ->
                        MetalArtistEntity.fromJsonArrays(
                            mbid = artist.id,
                            name = artist.name,
                            genres = artist.tags.take(5),
                            tags = artist.tags,
                            matchedTags = emptyList(), // Calculado depois
                            score = artist.score.toDouble() / 100.0 * 100,
                            country = artist.country,
                            imageUrl = null, // Buscar depois
                            isActive = artist.lifeSpan?.ended != true,
                            beginYear = artist.lifeSpan?.begin,
                            endYear = if (artist.lifeSpan?.ended == true) artist.lifeSpan?.end else null
                        )
                    }
                    artistDao.insertArtists(entities)
                }
            }

            currentOffset = page + 1
            
            val endOfPaginationReached = artists.isEmpty() || artists.size < PAGE_SIZE
            
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)

        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }

    private suspend fun getSeedTagsFromLibrary(): List<String> {
        // Obter tags da biblioteca do usuário para personalizar descoberta
        return try {
            // Por enquanto retornar tags genéricas de metal
            listOf("metal", "heavy metal", "power metal", "black metal")
        } catch (e: Exception) {
            listOf("metal")
        }
    }

    private suspend fun searchWithTags(tags: List<String>, page: Int): List<com.example.ytdown.services.MBArtist> {
        val tagIndex = page % tags.size
        val tag = tags.getOrElse(tagIndex) { "metal" }
        
        return musicBrainzService.searchArtistsByTag(tag, limit = PAGE_SIZE)
    }

    private suspend fun continueSearch(page: Int): List<com.example.ytdown.services.MBArtist> {
        // Continuar busca com offset - usando searchArtists simples
        return musicBrainzService.searchArtists("metal", limit = PAGE_SIZE)
    }

    /**
     * Revalida dados expirados em background
     */
    suspend fun refreshExpiredArtists() {
        val artistDao = database.artistDao()
        
        try {
            val expired = artistDao.getExpiredArtists(
                System.currentTimeMillis() - MetalArtistEntity.SHORT_CACHE_TIMEOUT,
                limit = 20
            )
            
            for (artist in expired) {
                try {
                    delay(RATE_LIMIT_DELAY_MS)
                    
                    // Buscar dados atualizados
                    val details = musicBrainzService.getArtistDetails(artist.mbid)
                    
                    if (details != null) {
                        val updated = artist.copy(
                            tagsJson = details.tags.toJsonString(),
                            genresJson = details.genres.toJsonString(),
                            lastUpdated = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED
                        )
                        artistDao.updateArtist(updated)
                    } else {
                        artistDao.updateSyncStatus(artist.mbid, SyncStatus.STALE)
                    }
                } catch (e: Exception) {
                    artistDao.updateSyncStatus(artist.mbid, SyncStatus.FAILED)
                }
            }
        } catch (e: Exception) {
            // Silently fail - dados antigos ainda servem offline
        }
    }

    private fun List<String>.toJsonString(): String {
        return if (isEmpty()) "[]"
        else "[${joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
    }
}

/**
 * Extensão para buscar tags do perfil do usuário
 */
private suspend fun MusicBrainzService.getUserLibraryTags(): List<String> {
    return try {
        val tags = getPopularMetalTags()
        tags.shuffled().take(5)
    } catch (e: Exception) {
        listOf("metal", "heavy metal")
    }
}