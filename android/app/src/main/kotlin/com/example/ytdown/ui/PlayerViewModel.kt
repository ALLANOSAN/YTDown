package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    val currentTrack = playerManager.currentTrack
    val isPlaying = playerManager.isPlaying
    
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    // Art Toggle Logic (Portado do Flutter)
    private val _showArtistImage = MutableStateFlow(false)
    val showArtistImage: StateFlow<Boolean> = _showArtistImage
    private var artToggleJob: Job? = null

    init {
        updateProgress()
        startArtToggleTimer()
    }

    private fun updateProgress() {
        viewModelScope.launch {
            while (true) {
                _position.value = playerManager.player.currentPosition
                _duration.value = playerManager.player.duration.coerceAtLeast(0L)
                delay(1000)
            }
        }
    }

    private fun startArtToggleTimer() {
        artToggleJob?.cancel()
        artToggleJob = viewModelScope.launch {
            while (true) {
                delay(10000) // 10 segundos
                if (currentTrack.value != null) {
                    _showArtistImage.value = !_showArtistImage.value
                }
            }
        }
    }

    fun playTrack(item: DownloadItemEntity) {
        viewModelScope.launch {
            playerManager.play(item)
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }
}
