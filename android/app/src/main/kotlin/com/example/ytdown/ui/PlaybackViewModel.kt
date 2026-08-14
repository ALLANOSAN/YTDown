package com.example.ytdown.ui

import com.example.ytdown.core.domain.DownloadItemEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.audio.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

import com.example.ytdown.core.artwork.ArtworkRotationController
import com.example.ytdown.core.artwork.ArtworkMode
import com.example.ytdown.utils.LocalLogger
// ...
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val rotationController: ArtworkRotationController
) : ViewModel() {

    init {
        LocalLogger.debug("VIEWMODEL CREATED", tag = "PlaybackViewModel")
    }

    val playbackUiState: StateFlow<PlaybackUiState> = 
        kotlinx.coroutines.flow.combine(controller.uiState, rotationController.artworkMode) { state, mode ->
            PlaybackUiState(
                currentTrack = state.currentTrack,
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                currentPositionMs = state.positionMs,
                durationMs = state.durationMs,
                isShuffleEnabled = state.isShuffleEnabled,
                repeatMode = state.repeatMode,
                spectrumData = state.spectrumData,
                artworkMode = mode
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackUiState())

    // Todos os comandos passam pelo PlaybackController que usa MediaController
    // Isso garante que o Media3 gerencie o serviço e a notificação corretamente
    fun togglePlayPause() = controller.togglePlayPause()
    fun playNext() = controller.playNext()
    fun playPrevious() = controller.playPrevious()
    fun rewind() { controller.seekTo(maxOf(0, controller.positionMs - 10_000L)) }
    fun forward() { controller.seekTo(controller.positionMs + 10_000L) }
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun toggleShuffle() { controller.updateShuffle(!controller.uiState.value.isShuffleEnabled) }
    fun toggleRepeat() {
        val nextMode = when (controller.uiState.value.repeatMode) {
            0 -> 1; 1 -> 2; else -> 0
        }
        controller.updateRepeatMode(nextMode)
    }

    fun updateArtworkPaths(albumPath: String?, artistPath: String?) {
        rotationController.updateArtworkPaths(albumPath, artistPath)
    }
    
    fun playTrack(track: DownloadItemEntity) {
        controller.playTrack(track)
    }

    fun playPlaylist(tracks: List<DownloadItemEntity>, startIndex: Int = 0) {
        controller.playPlaylist(tracks, startIndex)
    }
}
