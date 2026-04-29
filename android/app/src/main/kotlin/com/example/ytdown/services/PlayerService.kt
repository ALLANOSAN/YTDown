package com.example.ytdown.services

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerService @Inject constructor(
    private val manager: MusicPlayerManager
) {
    val currentTrack: StateFlow<DownloadItemEntity?> = manager.currentTrack
    val repeatMode = manager.repeatMode
    val isShuffleEnabled = manager.isShuffleEnabled

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
