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

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val actionDispatcher: PlaybackActionDispatcher
) : ViewModel() {

    val playbackUiState: StateFlow<PlaybackUiState> = controller.uiState.map { state ->
        PlaybackUiState(
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            currentPositionMs = state.positionMs,
            durationMs = state.durationMs,
            isShuffleEnabled = state.isShuffleEnabled,
            repeatMode = state.repeatMode,
            spectrumData = FloatArray(64) // Integração FFT real virá aqui
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackUiState())

    fun togglePlayPause() = actionDispatcher.playPause()
    fun next() = actionDispatcher.next()
    fun previous() = actionDispatcher.previous()
    fun seekTo(positionMs: Long) = actionDispatcher.seekTo(positionMs)
    fun toggleShuffle() = actionDispatcher.toggleShuffle()
    fun toggleRepeat() = actionDispatcher.toggleRepeatMode()
    
    fun playTrack(track: DownloadItemEntity) {
        controller.playTrack(track)
    }

    fun playPlaylist(tracks: List<DownloadItemEntity>, startIndex: Int = 0) {
        controller.playPlaylist(tracks, startIndex)
    }
}
