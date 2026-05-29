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
    val downloadProgress: Map<String, Float> = emptyMap(),
    // Estado do dialog de formato
    val showFormatDialog: Boolean = false,
    val pendingAlbumName: String = "",
    val pendingAlbumYear: String? = null,
    val selectedDownloadType: com.example.ytdown.core.domain.DownloadType = com.example.ytdown.core.domain.DownloadType.AUDIO,
    val selectedFormat: String = "m4a",
    val selectedQuality: String = "192",
    val audioFormats: List<String> = listOf("mp3", "m4a", "flac", "opus", "ogg"),
    val videoFormats: List<String> = listOf("mp4", "mkv"),
    val audioBitrates: List<String> = listOf("128", "192", "256", "320", "lossless"),
    val videoResolutions: List<String> = listOf("360p", "480p", "720p", "1080p", "best")
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
     * Abre o dialog de formato primeiro
     */
    fun downloadBestAlbum(albumName: String? = null) {
        val targetAlbum = albumName ?: _uiState.value.bandInfo?.albums?.firstOrNull()?.title
            ?: "Best Songs"
        showFormatDialog(targetAlbum)
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
     * Baixa um álbum específico com formato customizado
     */
    fun downloadAlbumDirect(
        albumName: String,
        year: String? = null,
        downloadType: com.example.ytdown.core.domain.DownloadType = com.example.ytdown.core.domain.DownloadType.AUDIO,
        format: String = "m4a",
        quality: String = "192"
    ) {
        val bandName = _uiState.value.bandName
        if (bandName.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(downloadingAlbums = it.downloadingAlbums + albumName)
            }

            try {
                val result = discoveryRepository.downloadAlbum(bandName, albumName, year, downloadType, format, quality)
                
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

    // =====================================================
    // DIALOG DE FORMATO
    // =====================================================

    /**
     * Abre o dialog de escolha de formato para um álbum
     */
    fun showFormatDialog(albumName: String, year: String? = null) {
        _uiState.update {
            it.copy(
                showFormatDialog = true,
                pendingAlbumName = albumName,
                pendingAlbumYear = year,
                selectedDownloadType = com.example.ytdown.core.domain.DownloadType.AUDIO,
                selectedFormat = "m4a",
                selectedQuality = "192"
            )
        }
    }

    /**
     * Fecha o dialog de formato
     */
    fun dismissFormatDialog() {
        _uiState.update {
            it.copy(showFormatDialog = false, pendingAlbumName = "", pendingAlbumYear = null)
        }
    }

    /**
     * Atualiza o tipo de download (AUDIO/VIDEO)
     */
    fun updateDownloadType(type: com.example.ytdown.core.domain.DownloadType) {
        val defaultFormat = if (type == com.example.ytdown.core.domain.DownloadType.AUDIO) "m4a" else "mp4"
        val defaultQuality = if (type == com.example.ytdown.core.domain.DownloadType.AUDIO) "192" else "720p"
        _uiState.update {
            it.copy(
                selectedDownloadType = type,
                selectedFormat = defaultFormat,
                selectedQuality = defaultQuality
            )
        }
    }

    /**
     * Atualiza o formato selecionado
     */
    fun updateFormat(format: String) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    /**
     * Atualiza a qualidade/bitrate selecionado
     */
    fun updateQuality(quality: String) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    /**
     * Confirma o download com o formato selecionado
     */
    fun confirmDownload() {
        val state = _uiState.value
        val albumName = state.pendingAlbumName
        val year = state.pendingAlbumYear
        val format = state.selectedFormat
        val quality = state.selectedQuality
        val type = state.selectedDownloadType

        dismissFormatDialog()
        downloadAlbumDirect(albumName, year, type, format, quality)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}