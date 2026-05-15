package com.example.ytdown.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.DynamicBandInfo
import com.example.ytdown.core.infrastructure.DynamicMetalDiscoveryRepository
import com.example.ytdown.core.infrastructure.StorageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado da UI para a tela de detalhes da banda
 */
data class BandDetailsUIState(
    val bandName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val bandInfo: DynamicBandInfo? = null,
    val downloadingAlbums: Set<String> = emptySet(),
    val downloadedAlbums: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap()
)

@HiltViewModel
class BandDetailsViewModel @Inject constructor(
    private val discoveryRepository: DynamicMetalDiscoveryRepository,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BandDetailsUIState())
    val uiState: StateFlow<BandDetailsUIState> = _uiState.asStateFlow()

    init {
        val bandName = savedStateHandle.get<String>("bandName") ?: ""
        if (bandName.isNotBlank()) {
            loadBandDetails(bandName)
        }
    }

    fun loadBandDetails(bandName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, bandName = bandName) }

            try {
                val bandInfo = discoveryRepository.getBandDiscography(bandName)
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandInfo = bandInfo,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Erro ao carregar detalhes: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Baixa o melhor álbum da banda (ou um álbum específico)
     */
    fun downloadBestAlbum(albumName: String? = null) {
        val bandName = _uiState.value.bandName
        if (bandName.isBlank()) return

        viewModelScope.launch {
            val targetAlbum = albumName ?: _uiState.value.bandInfo?.albums?.firstOrNull()?.title
                ?: "Best Songs"

            _uiState.update { 
                it.copy(downloadingAlbums = it.downloadingAlbums + targetAlbum) 
            }

            try {
                // Cria a query de busca - tenta encontrar o álbum completo
                val query = "ytsearch1:\"$bandName $targetAlbum full album\""
                
                scheduler.schedule(
                    url = VideoUrl(query),
                    path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                    meta = MediaMetadata(
                        MediaTitle(targetAlbum),
                        ArtistName(bandName),
                        AlbumName("Descoberta Metal")
                    ),
                    options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
                )

                _uiState.update { 
                    it.copy(
                        downloadingAlbums = it.downloadingAlbums - targetAlbum,
                        downloadedAlbums = it.downloadedAlbums + targetAlbum
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        downloadingAlbums = it.downloadingAlbums - targetAlbum,
                        error = "Erro ao baixar: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Baixa todos os álbuns da banda
     */
    fun downloadAllAlbums() {
        val bandName = _uiState.value.bandName
        val albums = _uiState.value.bandInfo?.albums ?: return

        viewModelScope.launch {
            albums.take(5).forEachIndexed { index, album ->
                downloadAlbumDirect(album.title, album.year)
                if (index < albums.size - 1) {
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }
    }

    /**
     * Baixa um álbum específico
     */
    fun downloadAlbumDirect(albumName: String, year: String? = null) {
        val bandName = _uiState.value.bandName
        if (bandName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { 
                it.copy(downloadingAlbums = it.downloadingAlbums + albumName) 
            }

            try {
                val result = discoveryRepository.downloadAlbum(bandName, albumName, year)
                
                when (result) {
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                downloadingAlbums = it.downloadingAlbums - albumName,
                                downloadedAlbums = it.downloadedAlbums + albumName
                            ) 
                        }
                    }
                    is com.example.ytdown.core.infrastructure.MetalDownloadResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                downloadingAlbums = it.downloadingAlbums - albumName,
                                error = "Erro ao baixar $albumName: ${result.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        downloadingAlbums = it.downloadingAlbums - albumName,
                        error = "Erro ao baixar $albumName: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}