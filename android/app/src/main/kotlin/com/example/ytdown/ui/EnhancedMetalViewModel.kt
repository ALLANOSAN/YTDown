package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.infrastructure.DynamicBandInfo
import com.example.ytdown.core.infrastructure.DynamicMetalDiscoveryRepository
import com.example.ytdown.core.infrastructure.MetalDownloadResult
import com.example.ytdown.data.local.metal.entities.*
import com.example.ytdown.data.repository.metal.ListeningStatsResult
import com.example.ytdown.data.repository.metal.MetalRepository
import com.example.ytdown.services.DiscoveredArtist
import com.example.ytdown.services.DiscoveredStyle
import com.example.ytdown.services.DiscoveryResult
import com.example.ytdown.services.DynamicMusicDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado da UI do Metal - Versão Enterprise
 */
sealed interface MetalUIState {
    data object Loading : MetalUIState
    data object Initializing : MetalUIState
    data class Success(
        val artists: Flow<PagingData<MetalArtistEntity>>,
        val detectedStyles: List<DiscoveredStyle> = emptyList(),
        val recommendedArtists: List<DiscoveredArtist> = emptyList(),
        val profile: MusicProfileEntity? = null,
        val stats: ListeningStatsResult? = null,
        val cachedArtistCount: Int = 0,
        val isOffline: Boolean = false
    ) : MetalUIState
    data class Error(val message: String, val isOffline: Boolean = false) : MetalUIState
}

/**
 * ViewModel Enterprise do Sistema Metal
 * 
 * Implementa:
 * - Paging 3 com RemoteMediator
 * - Cache Offline First
 * - Histórico de escuta
 * - Perfil musical dinâmico
 * - Estados de UI completos
 * - Tratamento de erros
 */
@HiltViewModel
class EnhancedMetalViewModel @Inject constructor(
    private val metalRepository: MetalRepository,
    private val dynamicDiscovery: DynamicMusicDiscovery,
    private val discoveryRepository: DynamicMetalDiscoveryRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    // =====================================================
    // STATE
    // =====================================================
    
    private val _uiState = MutableStateFlow<MetalUIState>(MetalUIState.Initializing)
    val uiState: StateFlow<MetalUIState> = _uiState.asStateFlow()
    
    // Paging de artistas
    val artistsPagingFlow: Flow<PagingData<MetalArtistEntity>> = 
        metalRepository.getPagedArtists().cachedIn(viewModelScope)
    
    // Observação de perfil
    val musicProfile: Flow<MusicProfileEntity?> = metalRepository.observeMusicProfile()
    
    // =====================================================
    // INICIALIZAÇÃO
    // =====================================================
    
    init {
        initialize()
    }
    
    private fun initialize() {
        viewModelScope.launch {
            _uiState.value = MetalUIState.Initializing
            
            try {
                // Verificar se há dados em cache
                val cachedCount = metalRepository.getCachedArtistCount()
                
                if (cachedCount > 0) {
                    // Usar cache existente - já temos dados offline
                    loadFromCache()
                } else {
                    // Primeira vez - descobrir da biblioteca
                    performDiscovery()
                }
                
                // Gerar/atualizar perfil musical
                updateMusicProfile()
                
            } catch (e: Exception) {
                _uiState.value = MetalUIState.Error(
                    message = "Erro ao inicializar: ${e.message}",
                    isOffline = false
                )
            }
        }
    }
    
    /**
     * Carrega dados do cache (modo offline)
     */
    private suspend fun loadFromCache() {
        try {
            val cachedCount = metalRepository.getCachedArtistCount()
            val artists = metalRepository.getPagedArtists()
            val profile = metalRepository.observeMusicProfile().first()
            val stats = try {
                metalRepository.getListeningStats()
            } catch (e: Exception) {
                null
            }
            
            _uiState.value = MetalUIState.Success(
                artists = artists,
                profile = profile,
                stats = stats,
                cachedArtistCount = cachedCount,
                isOffline = false
            )
        } catch (e: Exception) {
            _uiState.value = MetalUIState.Error(
                message = "Erro ao carregar cache: ${e.message}",
                isOffline = true
            )
        }
    }
    
    /**
     * Executa descoberta musical dinâmica
     */
    fun performDiscovery() {
        viewModelScope.launch {
            _uiState.value = MetalUIState.Loading
            
            try {
                // Obter artistas da biblioteca
                val libraryItems = downloadRepository.stream().first()
                val libraryArtists = libraryItems
                    .mapNotNull { it.artist?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
                
                // Executar descoberta
                val result = if (libraryArtists.isNotEmpty()) {
                    metalRepository.discoverFromLibrary(libraryArtists)
                } else {
                    // Biblioteca vazia - usar descoberta padrão
                    val defaultTags = listOf("metal", "heavy metal", "power metal")
                    val artists = dynamicDiscovery.discoverWithSeedStyles(defaultTags)
                    
                    DiscoveryResult(
                        success = true,
                        detectedStyles = defaultTags.map { DiscoveredStyle(it, 1, "default") },
                        recommendedArtists = artists
                    )
                }
                
                // Atualizar UI
                if (result.success) {
                    val artists = metalRepository.getPagedArtists()
                    val profile = metalRepository.observeMusicProfile().first()
                    val stats = try {
                        metalRepository.getListeningStats()
                    } catch (e: Exception) {
                        null
                    }
                    
                    _uiState.value = MetalUIState.Success(
                        artists = artists,
                        detectedStyles = result.detectedStyles,
                        recommendedArtists = result.recommendedArtists,
                        profile = profile,
                        stats = stats,
                        cachedArtistCount = metalRepository.getCachedArtistCount(),
                        isOffline = false
                    )
                } else {
                    _uiState.value = MetalUIState.Error(
                        message = result.error ?: "Erro na descoberta",
                        isOffline = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MetalUIState.Error(
                    message = "Erro: ${e.message}",
                    isOffline = false
                )
            }
        }
    }
    
    /**
     * Atualiza o perfil musical baseado no histórico de escuta
     */
    private suspend fun updateMusicProfile() {
        try {
            metalRepository.generateMusicProfile()
        } catch (e: Exception) {
            // Silently fail - perfil é opcional
        }
    }
    
    // =====================================================
    // AÇÕES DO USUÁRIO
    // =====================================================
    
    /**
     * Baixa músicas de uma banda
     */
    fun downloadBand(bandName: String) {
        viewModelScope.launch {
            try {
                val result = discoveryRepository.downloadBandBestSongs(bandName)
                // Feedback será shown via state update
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Baixa um álbum específico
     */
    fun downloadAlbum(bandName: String, albumTitle: String, year: String? = null) {
        viewModelScope.launch {
            try {
                val result = discoveryRepository.downloadAlbum(bandName, albumTitle, year)
                // Feedback via state
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Carrega detalhes de uma banda
     */
    suspend fun loadBandDetails(bandName: String): DynamicBandInfo {
        return discoveryRepository.getBandDiscography(bandName)
    }
    
    /**
     * Registra reprodução (para histórico e perfil)
     */
    fun registerPlayback(
        artistName: String,
        artistMbid: String? = null,
        albumName: String? = null,
        genre: String? = null
    ) {
        viewModelScope.launch {
            try {
                metalRepository.registerPlayback(
                    artistName = artistName,
                    artistMbid = artistMbid,
                    albumName = albumName,
                    genre = genre
                )
                
                // Atualizar play count do artista
                artistMbid?.let { mbid ->
                    // Increment local play count
                }
            } catch (e: Exception) {
                // Silently fail - histórico é opcional
            }
        }
    }
    
    /**
     * Alterna favorito
     */
    fun toggleFavorite(mbid: String) {
        viewModelScope.launch {
            try {
                metalRepository.toggleFavorite(mbid)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Busca artistas
     */
    fun searchArtists(query: String) {
        viewModelScope.launch {
            try {
                val results = metalRepository.searchArtists(query)
                // Update state with search results
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Limpa cache expirado
     */
    fun clearExpiredCache() {
        viewModelScope.launch {
            try {
                metalRepository.clearExpiredCache()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Atualiza/recarrega
     */
    fun refresh() {
        performDiscovery()
    }
    
    /**
     * Retry em caso de erro
     */
    fun retry() {
        when (val current = _uiState.value) {
            is MetalUIState.Error -> {
                if (current.isOffline) {
                    viewModelScope.launch {
                        loadFromCache()
                    }
                } else {
                    performDiscovery()
                }
            }
            else -> performDiscovery()
        }
    }
}

/**
 * Extension para converter DiscoveryResult do service
 */
private fun com.example.ytdown.services.DiscoveryResult.toState(): DiscoveryResult {
    return DiscoveryResult(
        success = this.success,
        error = this.error,
        detectedStyles = this.detectedStyles,
        recommendedArtists = this.recommendedArtists,
        analyzedArtists = this.analyzedArtists,
        totalTagsFound = this.totalTagsFound
    )
}