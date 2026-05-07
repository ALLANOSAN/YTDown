package com.example.ytdown.ui

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.utils.HapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URL
import javax.inject.Inject
import androidx.media3.common.Player

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val hapticManager: HapticManager
) : ViewModel() {

    val currentTrack = playerManager.currentTrack
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val isShuffleEnabled = playerManager.isShuffleEnabled
    val repeatMode = playerManager.repeatMode

    private val _dominantColor = MutableStateFlow<Int?>(null)
    val dominantColor = _dominantColor.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    private val _showArtistImage = MutableStateFlow(false)
    val showArtistImage = _showArtistImage.asStateFlow()

    private var artworkTimer: Job? = null
    private var progressTicker: Job? = null
    private var sleepTimerJob: Job? = null
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

        // Extração de paleta quando a música muda
        viewModelScope.launch {
            currentTrack.collect { track ->
                track?.let { updatePalette(it) }
            }
        }
    }

    private fun updatePalette(track: DownloadItemEntity) {
        val imageUrl = track.albumImageUrl?.takeIf { it.isNotBlank() } ?: track.thumbnailPath
        if (imageUrl.isNullOrBlank()) {
            _dominantColor.value = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (imageUrl.startsWith("http")) {
                    BitmapFactory.decodeStream(URL(imageUrl).openConnection().getInputStream())
                } else if (!imageUrl.startsWith("content://")) {
                    BitmapFactory.decodeFile(imageUrl)
                } else {
                    null
                }

                bitmap?.let {
                    Palette.from(it).generate { palette ->
                        _dominantColor.value = palette?.getVibrantColor(0xFF8A2BE2.toInt())
                            ?: palette?.getDominantColor(0xFF8A2BE2.toInt())
                    }
                }
            } catch (e: Exception) {
                // Falha silenciosa
            }
        }
    }

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

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        
        if (minutes != null) {
            hapticManager.selection()
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                playerManager.pause()
                _sleepTimerMinutes.value = null
            }
        }
    }

    fun togglePlayPause() {
        if (playerManager.currentTrack.value == null) return
        hapticManager.impactMedium()
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

    fun toggleShuffle() {
        hapticManager.selection()
        playerManager.toggleShuffle()
    }
    
    fun toggleRepeatMode() {
        hapticManager.selection()
        playerManager.toggleRepeatMode()
    }
    
    fun playTrack(item: DownloadItemEntity) {
        hapticManager.impactLight()
        playerManager.playTrack(item)
    }
    
    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) {
        hapticManager.impactLight()
        playerManager.playPlaylist(items, startIndex)
    }
    
    fun next() {
        hapticManager.impactLight()
        playerManager.next()
    }
    
    fun previous() {
        hapticManager.impactLight()
        playerManager.previous()
    }
    
    fun seekTo(pos: Long) = playerManager.seekTo(pos)
    fun restoreLastPosition() = playerManager.restoreLastPosition()

    override fun onCleared() {
        artworkTimer?.cancel()
        progressTicker?.cancel()
        sleepTimerJob?.cancel()
        playerManager.getPlayer().removeListener(playerListener)
        super.onCleared()
    }
}
