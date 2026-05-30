package com.example.ytdown.ui

import com.example.ytdown.core.domain.DownloadItemEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.audio.PlaybackActionDispatcher
import com.example.ytdown.core.audio.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

import com.example.ytdown.core.artwork.ArtworkRotationController
import com.example.ytdown.core.artwork.ArtworkMode
// ...
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val actionDispatcher: PlaybackActionDispatcher,
    private val rotationController: ArtworkRotationController
) : ViewModel() {

    init {
        android.util.Log.e("PlaybackViewModel", "VIEWMODEL CREATED")
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

    fun togglePlayPause() = actionDispatcher.playPause()
    fun playNext() = actionDispatcher.next()
    fun playPrevious() = actionDispatcher.previous()
    fun rewind() = actionDispatcher.rewind()
    fun forward() = actionDispatcher.forward()
    fun seekTo(positionMs: Long) = actionDispatcher.seekTo(positionMs)
    fun toggleShuffle() = actionDispatcher.toggleShuffle()
    fun toggleRepeat() = actionDispatcher.toggleRepeatMode()

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
