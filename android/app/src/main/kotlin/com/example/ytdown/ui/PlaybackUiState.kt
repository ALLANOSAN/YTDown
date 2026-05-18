package com.example.ytdown.ui

import com.example.ytdown.core.domain.DownloadItemEntity

import com.example.ytdown.core.artwork.ArtworkMode

/**
 * Representa o estado visual completo do player.
 * Fonte única de verdade para toda a UI (Fullscreen, MiniPlayer, Notification).
 */
data class PlaybackUiState(
    val currentTrack: DownloadItemEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0: off, 1: all, 2: one
    val spectrumData: FloatArray = FloatArray(64),
    val artworkMode: ArtworkMode = ArtworkMode.ALBUM
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaybackUiState) return false
        if (currentTrack != other.currentTrack) return false
        if (isPlaying != other.isPlaying) return false
        if (isBuffering != other.isBuffering) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (durationMs != other.durationMs) return false
        if (isShuffleEnabled != other.isShuffleEnabled) return false
        if (repeatMode != other.repeatMode) return false
        if (!spectrumData.contentEquals(other.spectrumData)) return false
        if (artworkMode != other.artworkMode) return false
        return true
    }

    override fun hashCode(): Int {
        var result = currentTrack?.hashCode() ?: 0
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + isBuffering.hashCode()
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + isShuffleEnabled.hashCode()
        result = 31 * result + repeatMode.hashCode()
        result = 31 * result + spectrumData.contentHashCode()
        result = 31 * result + artworkMode.hashCode()
        return result
    }
}
