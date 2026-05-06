package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.core.domain.DownloadItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

import androidx.media3.common.Player

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    val currentTrack = playerManager.currentTrack
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val isShuffleEnabled = playerManager.isShuffleEnabled
    val repeatMode = playerManager.repeatMode

    private val _showArtistImage = MutableStateFlow(false)
    val showArtistImage = _showArtistImage.asStateFlow()

    private var artworkTimer: Job? = null
    private var progressTicker: Job? = null
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                duration.value = playerManager.getPlayer().duration.coerceAtLeast(0L)
            }
        }
    }

    init {
        startArtworkTimer()
        playerManager.getPlayer().addListener(playerListener)
        startProgressTicker()
        isPlaying.value = playerManager.getPlayer().isPlaying
    }

    /**
     * Migrado do Flutter (MusicPlayerScreen -> _startArtworkToggleTimer):
     * Alterna suavemente entre a capa do álbum e a foto do artista a cada 10 segundos.
     */
    private fun startArtworkTimer() {
        artworkTimer?.cancel()
        artworkTimer = viewModelScope.launch {
            while (isActive) {
                delay(10000)
                _showArtistImage.value = !_showArtistImage.value
            }
        }
    }

    private fun startProgressTicker() {
        progressTicker?.cancel()
        progressTicker = viewModelScope.launch {
            while (isActive) {
                val player = playerManager.getPlayer()
                position.value = player.currentPosition
                duration.value = player.duration.coerceAtLeast(0L)
                delay(200)
            }
        }
    }

    fun togglePlayPause() {
        if (playerManager.currentTrack.value == null) return
        val player = playerManager.getPlayer()
        val currentlyPlaying = player.isPlaying

        if (currentlyPlaying) {
            playerManager.pause()
        }
        if (!currentlyPlaying) {
            playerManager.resume()
        }

        isPlaying.value = playerManager.getPlayer().isPlaying
    }

    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeatMode() = playerManager.toggleRepeatMode()
    fun playTrack(item: DownloadItemEntity) = playerManager.playTrack(item)
    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) = playerManager.playPlaylist(items, startIndex)
    fun next() = playerManager.next()
    fun previous() = playerManager.previous()
    fun seekTo(pos: Long) = playerManager.seekTo(pos)
    fun restoreLastPosition() = playerManager.restoreLastPosition()

    override fun onCleared() {
        artworkTimer?.cancel()
        progressTicker?.cancel()
        playerManager.getPlayer().removeListener(playerListener)
        super.onCleared()
    }
}
