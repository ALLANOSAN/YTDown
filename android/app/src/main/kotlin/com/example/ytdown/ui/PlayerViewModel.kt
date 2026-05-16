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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: MusicPlayerManager,
    private val hapticManager: HapticManager,
    private val lyricsService: com.example.ytdown.services.LyricsService
) : ViewModel() {

    val uiState = playerManager.uiState
    
    private val _lyrics = MutableStateFlow<com.example.ytdown.services.LyricsResponse?>(null)
    val lyrics = _lyrics.asStateFlow()

    private val _dominantColor = MutableStateFlow<Int?>(null)
    val dominantColor = _dominantColor.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    private val _showArtistImage = MutableStateFlow(false)
    val showArtistImage = _showArtistImage.asStateFlow()

    private var artworkTimer: Job? = null
    private var progressTicker: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        startArtworkTimer()
        startProgressTicker()

        // Extração de paleta e busca de letras quando a música muda
        viewModelScope.launch {
            uiState.map { it.currentTrack }.distinctUntilChanged().collect { track ->
                track?.let { 
                    updatePalette(it)
                    fetchLyrics(it)
                } ?: run {
                    _lyrics.value = null
                }
            }
        }
    }

    private fun fetchLyrics(track: DownloadItemEntity) {
        viewModelScope.launch {
            _lyrics.value = lyricsService.getLyrics(
                artist = track.artist ?: "Unknown",
                title = track.title,
                album = track.album
            )
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
                    BitmapFactory.decodeStream(java.net.URL(imageUrl).openConnection().getInputStream())
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
        // O progresso agora é atualizado automaticamente pelo BassPlaybackEngine 
        // e refletido no stateManager.uiState
        progressTicker?.cancel()
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
        if (uiState.value.currentTrack == null) return
        hapticManager.impactMedium()
        
        if (uiState.value.isPlaying) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
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
        super.onCleared()
    }
}
