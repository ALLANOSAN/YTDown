package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.infrastructure.DynamicBandInfo
import com.example.ytdown.core.infrastructure.DynamicMetalDiscoveryRepository
import com.example.ytdown.services.DiscoveredArtist
import com.example.ytdown.services.DiscoveredStyle
import com.example.ytdown.services.DynamicMusicDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado da UI de Descoberta Dinâmica de Metal
 */
data class DynamicMetalUIState(
    // Estados de carregamento
    val isLoading: Boolean = true,
    val isDiscoveringMore: Boolean = false,
    
    // Estilos detectados automaticamente (sem hardcoded)
    val detectedStyles: List<DiscoveredStyle> = emptyList(),
    
    // Artistas recomendados
    val recommendedArtists: List<DiscoveredArtist> = emptyList(),
    
    // Estatísticas
    val analyzedArtistsCount: Int = 0,
    val totalTagsFound: Int = 0,
    
    // Erro
    val error: String? = null,
    
    // Bandas já baixadas
    val downloadedBands: Set<String> = emptySet()
)

@HiltViewModel
class DynamicMetalViewModel @Inject constructor(
    private val discoveryRepository: DynamicMetalDiscoveryRepository,
    private val dynamicDiscovery: DynamicMusicDiscovery
) : ViewModel() {

    private val _uiState = MutableStateFlow(DynamicMetalUIState())
    val uiState: StateFlow<DynamicMetalUIState> = _uiState.asStateFlow()

    init {
        performDiscovery()
    }

    /**
     * Executa a descoberta musical dinâmica
     * Analisa a biblioteca real e detecta estilos automaticamente
     */
    fun performDiscovery() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(isLoading = true, error = null) 
            }

            try {
                val result = discoveryRepository.performDynamicDiscovery()

                if (result.success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detectedStyles = result.detectedStyles,
                            recommendedArtists = result.recommendedArtists,
                            analyzedArtistsCount = result.analyzedArtists,
                            totalTagsFound = result.totalTagsFound,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error ?: "Erro na descoberta"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Erro: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Carrega mais artistas recomendados
     */
    fun discoverMore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscoveringMore = true) }

            try {
                // Usar os estilos já detectados para buscar mais
                val currentStyles = _uiState.value.detectedStyles.map { it.name }
                val existingNames = _uiState.value.recommendedArtists.map { it.name.lowercase() }.toSet()

                val moreArtists = dynamicDiscovery.discoverWithSeedStyles(
                    seedTags = currentStyles,
                    excludeNames = existingNames
                )

                _uiState.update {
                    it.copy(
                        isDiscoveringMore = false,
                        recommendedArtists = (it.recommendedArtists + moreArtists).take(60)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDiscoveringMore = false,
                        error = "Erro ao buscar mais: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Baixa as melhores músicas de uma banda
     */
    fun downloadBand(bandName: String) {
        viewModelScope.launch {
            try {
                val result = discoveryRepository.downloadBandBestSongs(bandName)

                when (result) {
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Success -> {
                        _uiState.update {
                            it.copy(downloadedBands = it.downloadedBands + bandName)
                        }
                    }
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Error -> {
                        _uiState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Erro ao baixar: ${e.message}")
                }
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

                when (result) {
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Success -> {
                        _uiState.update {
                            it.copy(downloadedBands = it.downloadedBands + bandName)
                        }
                    }
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Error -> {
                        _uiState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Erro ao baixar álbum: ${e.message}")
                }
            }
        }
    }

    /**
     * Limpa o erro atual
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Recarrega a descoberta
     */
    fun refresh() {
        performDiscovery()
    }
}

/**
 * ViewModel para detalhes de uma banda específica
 */
@HiltViewModel
class BandDetailsDynamicViewModel @Inject constructor(
    private val discoveryRepository: DynamicMetalDiscoveryRepository
) : ViewModel() {

    private val _bandInfo = MutableStateFlow<DynamicBandInfo?>(null)
    val bandInfo: StateFlow<DynamicBandInfo?> = _bandInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, String>> = _downloadProgress.asStateFlow()

    fun loadBandDetails(bandName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val info = discoveryRepository.getBandDiscography(bandName)
                _bandInfo.value = info
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Erro ao carregar: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun downloadAlbum(bandName: String, albumTitle: String, year: String?) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (albumTitle to "Baixando...")

            try {
                val result = discoveryRepository.downloadAlbum(bandName, albumTitle, year)
                
                when (result) {
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Success -> {
                        _downloadProgress.value = _downloadProgress.value - albumTitle
                    }
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Error -> {
                        _downloadProgress.value = _downloadProgress.value + (albumTitle to "Erro")
                    }
                }
            } catch (e: Exception) {
                _downloadProgress.value = _downloadProgress.value + (albumTitle to "Erro")
            }
        }
    }

    fun downloadAllAlbums() {
        val band = _bandInfo.value ?: return

        viewModelScope.launch {
            band.albums.take(5).forEach { album ->
                downloadAlbum(band.name, album.title, album.year)
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}