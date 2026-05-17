package com.example.ytdown.core.audio

import android.util.Log
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaybackUiState - ÚNICA fonte de verdade para o estado do player.
 * Todos os componentes (UI, MediaSession, Notification, Lockscreen) devem usar este estado.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrack: DownloadItemEntity? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val repeatMode: Int = 0, // 0: OFF, 1: ALL, 2: ONE
    val isShuffleEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * PlaybackController - SINGLE SOURCE OF TRUTH para todo o estado de reprodução.
 * Substitui COMPLETAMENTE o PlaybackStateManager.
 * 
 * Responsabilidades:
 * - Estado centralizado para UI, MediaSession, Notification, Lockscreen
 * - Sincronização entre todos os pontos de controle
 * - Integração com BASS Engine
 */
@Singleton
class PlaybackController @Inject constructor(
    private val engineProvider: javax.inject.Provider<BassPlaybackEngine>
) {

    companion object {
        private const val TAG = "PlaybackController"
    }

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    /**
     * Atualiza o estado de forma atômica
     */
    fun updateState(transform: (PlaybackUiState) -> PlaybackUiState) {
        val oldState = _uiState.value
        val newState = transform(oldState)
        _uiState.value = newState
        
        // Log para debugging
        if (oldState.isPlaying != newState.isPlaying) {
            Log.d(TAG, "Playing state changed: ${oldState.isPlaying} -> ${newState.isPlaying}")
        }
        if (oldState.currentTrack?.id != newState.currentTrack?.id) {
            Log.d(TAG, "Track changed: ${oldState.currentTrack?.title} -> ${newState.currentTrack?.title}")
        }
    }

    // ========== Métodos de Atualização ==========

    fun updatePlaying(isPlaying: Boolean) = updateState { 
        it.copy(isPlaying = isPlaying).also { Log.d(TAG, "updatePlaying: $isPlaying") } 
    }

    fun updateTrack(track: DownloadItemEntity?) = updateState { 
        it.copy(currentTrack = track).also { Log.d(TAG, "updateTrack: ${track?.title}") } 
    }

    fun updatePosition(posMs: Long) = updateState { 
        it.copy(positionMs = posMs) 
    }

    fun updateDuration(durMs: Long) = updateState { 
        it.copy(durationMs = durMs).also { Log.d(TAG, "updateDuration: $durMs ms") } 
    }

    fun updateBuffering(isBuffering: Boolean) = updateState { 
        it.copy(isBuffering = isBuffering).also { Log.d(TAG, "updateBuffering: $isBuffering") } 
    }

    fun updateVolume(volume: Float) = updateState { 
        it.copy(volume = volume) 
    }

    fun updateRepeatMode(mode: Int) = updateState { 
        it.copy(repeatMode = mode).also { Log.d(TAG, "updateRepeatMode: $mode") } 
    }

    fun updateShuffle(enabled: Boolean) = updateState { 
        it.copy(isShuffleEnabled = enabled).also { Log.d(TAG, "updateShuffle: $enabled") } 
    }

    fun setError(message: String?) = updateState { 
        it.copy(errorMessage = message).also { Log.e(TAG, "Error: $message") } 
    }
    
    fun playTrack(track: DownloadItemEntity) {
        engineProvider.get().play(track)
    }

    fun playPlaylist(tracks: List<DownloadItemEntity>, startIndex: Int = 0) {
        if (tracks.isNotEmpty()) {
            engineProvider.get().play(tracks[startIndex])
        }
    }

    // ========== Métodos de Acesso ==========

    val currentTrack: DownloadItemEntity?
        get() = _uiState.value.currentTrack

    val isPlaying: Boolean
        get() = _uiState.value.isPlaying

    val positionMs: Long
        get() = _uiState.value.positionMs

    val durationMs: Long
        get() = _uiState.value.durationMs
}
