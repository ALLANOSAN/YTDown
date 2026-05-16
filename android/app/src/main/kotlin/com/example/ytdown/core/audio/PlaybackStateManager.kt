package com.example.ytdown.core.audio

import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaybackUiState - Representação reativa do estado do player para a UI Compose.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val currentTrack: DownloadItemEntity? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val volume: Float = 1.0f,
    val repeatMode: Int = 0, // 0: OFF, 1: ALL, 2: ONE
    val isShuffleEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * PlaybackStateManager - Gerencia o estado reativo da reprodução.
 * Atua como a ponte entre o BassPlaybackEngine e os ViewModels.
 */
@Singleton
class PlaybackStateManager @Inject constructor() {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState = _uiState.asStateFlow()

    fun updatePlaying(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    fun updateTrack(track: DownloadItemEntity?) {
        _uiState.value = _uiState.value.copy(currentTrack = track)
    }

    fun updatePosition(posMs: Long) {
        _uiState.value = _uiState.value.copy(positionMs = posMs)
    }

    fun updateDuration(durMs: Long) {
        _uiState.value = _uiState.value.copy(durationMs = durMs)
    }

    fun updateBuffering(isBuffering: Boolean) {
        _uiState.value = _uiState.value.copy(isBuffering = isBuffering)
    }

    fun updateVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(volume = volume)
    }

    fun updateRepeatMode(mode: Int) {
        _uiState.value = _uiState.value.copy(repeatMode = mode)
    }

    fun updateShuffle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isShuffleEnabled = enabled)
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }
}
