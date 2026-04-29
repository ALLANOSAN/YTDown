package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.entities.FavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerProvider @Inject constructor(
    private val manager: MusicPlayerManager
) : ViewModel() {
    val currentTrack: StateFlow<DownloadItemEntity?> = manager.currentTrack
    val repeatMode: StateFlow<Int> = manager.repeatMode
    val isShuffleEnabled: StateFlow<Boolean> = manager.isShuffleEnabled

    fun playTrack(item: DownloadItemEntity) = manager.playTrack(item)
    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) = manager.playPlaylist(items, startIndex)
    fun pause() = manager.pause()
    fun resume() = manager.resume()
    fun next() = manager.next()
    fun previous() = manager.previous()
    fun seekTo(position: Long) = manager.seekTo(position)
    fun toggleRepeatMode() = manager.toggleRepeatMode()
    fun toggleShuffle() = manager.toggleShuffle()
}
