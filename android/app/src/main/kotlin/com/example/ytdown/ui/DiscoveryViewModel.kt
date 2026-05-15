package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.services.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryUIState(
    val suggestions: List<MBBand> = emptyList(),
    val albums: List<MBReleaseGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val musicBrainzService: MusicBrainzService,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoveryUIState())
    val uiState = _uiState.asStateFlow()

    fun loadSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val allDownloads = downloadRepository.stream().first()
            val currentArtists = allDownloads.mapNotNull { it.artist }.toSet()
            if (currentArtists.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = "Biblioteca vazia.") }
                return@launch
            }
            val response = musicBrainzService.discoverSimilarBands(currentArtists.random())
            if (response.success) {
                _uiState.update { it.copy(suggestions = response.bands ?: emptyList(), isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = response.error) }
            }
        }
    }

    fun loadAlbums(bandName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Buscar MBID primeiro
                val mbid = musicBrainzService.searchArtistId(bandName)
                if (mbid == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Banda não encontrada") }
                    return@launch
                }
                
                // Buscar release groups (álbuns)
                val releaseGroups = musicBrainzService.getArtistReleaseGroups(mbid)
                _uiState.update { it.copy(albums = releaseGroups, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun downloadAlbum(bandName: String, albumName: String) {
        viewModelScope.launch {
            try {
                scheduler.schedule(
                    url = VideoUrl("ytsearch1:\"$bandName $albumName - Full Album\""),
                    path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                    meta = MediaMetadata(MediaTitle(albumName), ArtistName(bandName), AlbumName(albumName)),
                    options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao baixar: ${e.message}") }
            }
        }
    }

    fun downloadBand(bandName: String) {
        viewModelScope.launch {
            try {
                scheduler.schedule(
                    url = VideoUrl("ytsearch1:\"$bandName - Best Songs\""),
                    path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                    meta = MediaMetadata(MediaTitle(bandName), ArtistName(bandName), AlbumName("Descoberta Metal")),
                    options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao baixar: ${e.message}") }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}