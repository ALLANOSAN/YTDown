package com.example.ytdown.ui

import com.example.ytdown.core.domain.DownloadItemEntity

/**
 * Representa o estado visual completo do player.
 * Fonte única de verdade para toda a UI (Fullscreen, MiniPlayer, Notification).
 */
data class PlaybackUiState(
    val currentTrack: DownloadItemEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0: None, 1: One, 2: All
    val spectrumData: FloatArray = FloatArray(64)
)
