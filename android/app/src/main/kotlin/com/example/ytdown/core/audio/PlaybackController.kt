package com.example.ytdown.core.audio

import android.util.Log
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    val errorMessage: String? = null,
    val spectrumData: FloatArray = FloatArray(64)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaybackUiState) return false
        if (isPlaying != other.isPlaying) return false
        if (isBuffering != other.isBuffering) return false
        if (currentTrack != other.currentTrack) return false
        if (positionMs != other.positionMs) return false
        if (durationMs != other.durationMs) return false
        if (volume != other.volume) return false
        if (repeatMode != other.repeatMode) return false
        if (isShuffleEnabled != other.isShuffleEnabled) return false
        if (errorMessage != other.errorMessage) return false
        if (!spectrumData.contentEquals(other.spectrumData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isPlaying.hashCode()
        result = 31 * result + isBuffering.hashCode()
        result = 31 * result + (currentTrack?.hashCode() ?: 0)
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + volume.hashCode()
        result = 31 * result + repeatMode.hashCode()
        result = 31 * result + isShuffleEnabled.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + spectrumData.contentHashCode()
        return result
    }
}

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

    init {
        android.util.Log.e("PlaybackController", "CONTROLLER CREATED")
    }

    companion object {
        private const val TAG = "PlaybackController"
    }

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var playlist: List<DownloadItemEntity> = emptyList()
    private var currentIndex: Int = -1

    /**
     * Atualiza o estado de forma atômica
     */
    fun updateState(transform: (PlaybackUiState) -> PlaybackUiState) {
        _uiState.update(transform)
    }

    // ========== Métodos de Atualização ==========

    fun updatePlaying(isPlaying: Boolean) = updateState { 
        it.copy(isPlaying = isPlaying)
    }

    fun updateTrack(track: DownloadItemEntity?) = updateState { 
        it.copy(currentTrack = track)
    }

    fun updatePosition(posMs: Long) = updateState { 
        it.copy(positionMs = posMs) 
    }

    fun updateDuration(durMs: Long) = updateState { 
        it.copy(durationMs = durMs)
    }

    fun updateBuffering(isBuffering: Boolean) = updateState { 
        it.copy(isBuffering = isBuffering)
    }

    fun updateVolume(volume: Float) = updateState { 
        it.copy(volume = volume) 
    }

    fun updateRepeatMode(mode: Int) = updateState { 
        it.copy(repeatMode = mode)
    }

    fun updateShuffle(enabled: Boolean) = updateState { 
        it.copy(isShuffleEnabled = enabled)
    }

    fun updateSpectrum(data: FloatArray) = updateState {
        it.copy(spectrumData = data)
    }

    fun setError(message: String?) = updateState { 
        it.copy(errorMessage = message)
    }
    
    fun playTrack(track: DownloadItemEntity) {
        engineProvider.get().play(track)
    }

    fun playPlaylist(tracks: List<DownloadItemEntity>, startIndex: Int = 0) {
        if (tracks.isNotEmpty()) {
            this.playlist = tracks
            this.currentIndex = startIndex
            engineProvider.get().play(tracks[startIndex])
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playTrack(playlist[currentIndex])
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        playTrack(playlist[currentIndex])
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
