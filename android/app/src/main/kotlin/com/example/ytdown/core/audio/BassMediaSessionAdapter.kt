package com.example.ytdown.core.audio

import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BassMediaSessionAdapter - Conecta o BASS engine ao Media3 Player interface.
 *
 * BasePlayer (Media3 1.4.x) tem apenas 1 método abstrato:
 *   seekTo(mediaItemIndex, positionMs, repeatMode, shuffleModeEnabled)
 *
 * Todos os outros métodos (play, pause, seekTo(1 arg), setMediaItems, etc.)
 * são finais no BasePlayer e delegam para esse único método.
 *
 * Para Android 16 Live Island (Now Bar) + MediaSession + Notification.
 */
@Singleton
class BassMediaSessionAdapter @Inject constructor(
    private val playbackController: PlaybackController,
    private val actionDispatcher: PlaybackActionDispatcher
) : BasePlayer() {

    private val listeners = mutableListOf<Player.Listener>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaItems = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private var repeatModeValue = Player.REPEAT_MODE_OFF
    private var shuffleEnabled = false

    init {
        // Notificar listeners quando estado do BASS mudar
        scope.launch {
            playbackController.uiState
                .map { Triple(it.isPlaying, it.isBuffering, it.currentTrack) }
                .distinctUntilChanged()
                .collect { (playing, buffering, track) ->
                    val state = when {
                        buffering -> Player.STATE_BUFFERING
                        track != null -> Player.STATE_READY
                        else -> Player.STATE_IDLE
                    }
                    listeners.forEach { it.onPlaybackStateChanged(state) }
                    listeners.forEach { it.onIsPlayingChanged(playing) }
                }
        }

        // Notificar listeners quando track mudar
        scope.launch {
            playbackController.uiState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    if (track != null) {
                        val idx = mediaItems.indexOfFirst { it.mediaId == track.id }
                        if (idx >= 0) currentIndex = idx
                        listeners.forEach {
                            it.onMediaItemTransition(
                                mediaItems.getOrNull(currentIndex),
                                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                            )
                        }
                    }
                }
        }
    }

    // ========== O ÚNICO método abstrato do BasePlayer ==========

    /**
     * BasePlayer delega TODOS os seeks para este método.
     * play() e pause() são controlados via setPlayWhenReady().
     */
    override fun seekTo(mediaItemIndex: Int, positionMs: Long, repeatMode: Int, shuffleModeEnabled: Boolean) {
        if (mediaItemIndex in mediaItems.indices) {
            currentIndex = mediaItemIndex
        }
        this.repeatModeValue = repeatMode
        this.shuffleEnabled = shuffleModeEnabled
        actionDispatcher.seekTo(positionMs)
    }

    // ========== Métodos NÃO-finais do BasePlayer ==========

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) actionDispatcher.play() else actionDispatcher.pause()
    }

    override fun getPlayWhenReady(): Boolean = playbackController.uiState.value.isPlaying

    override fun getPlaybackState(): Int {
        val state = playbackController.uiState.value
        return when {
            state.isBuffering -> Player.STATE_BUFFERING
            state.currentTrack != null -> Player.STATE_READY
            else -> Player.STATE_IDLE
        }
    }

    override fun getPlayerError(): PlaybackException? = null
    override fun isLoading(): Boolean = playbackController.uiState.value.isBuffering
    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getRepeatMode(): Int = repeatModeValue
    override fun setRepeatMode(repeatMode: Int) {
        repeatModeValue = repeatMode
        actionDispatcher.toggleRepeatMode()
    }

    override fun getShuffleModeEnabled(): Boolean = shuffleEnabled
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        shuffleEnabled = shuffleModeEnabled
        actionDispatcher.toggleShuffle()
    }

    override fun getCurrentPosition(): Long = playbackController.uiState.value.positionMs
    override fun getBufferedPosition(): Long = playbackController.uiState.value.positionMs
    override fun getTotalBufferedDuration(): Long = 0L

    override fun getVolume(): Float = playbackController.uiState.value.volume
    override fun setVolume(volume: Float) { playbackController.updateVolume(volume) }

    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}

    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: android.view.Surface?) {}
    override fun clearVideoSurfaceHolder(holder: android.view.SurfaceHolder?) {}
    override fun clearVideoSurfaceView(view: android.view.SurfaceView?) {}
    override fun clearVideoTextureView(view: android.view.TextureView?) {}
    override fun setVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurfaceHolder(holder: android.view.SurfaceHolder?) {}
    override fun setVideoSurfaceView(view: android.view.SurfaceView?) {}
    override fun setVideoTextureView(view: android.view.TextureView?) {}
    override fun getSurfaceSize(): androidx.media3.common.util.Size = androidx.media3.common.util.Size.UNKNOWN

    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}

    override fun getMediaMetadata(): MediaMetadata {
        return playbackController.uiState.value.currentTrack?.toMediaMetadata() ?: MediaMetadata.EMPTY
    }

    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}

    override fun getCurrentTimeline(): Timeline = BassTimeline(mediaItems)
    override fun getCurrentPeriodIndex(): Int = currentIndex
    override fun getCurrentMediaItemIndex(): Int = currentIndex

    override fun getAvailableCommands(): Player.Commands {
        return Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE
            )
            .build()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
        this.currentIndex = startIndex
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        for (i in (toIndex - 1) downTo fromIndex) {
            if (i in this.mediaItems.indices) this.mediaItems.removeAt(i)
        }
        this.mediaItems.addAll(fromIndex.coerceIn(0, this.mediaItems.size), mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        this.mediaItems.addAll(index, mediaItems)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        val itemsToMove = mediaItems.subList(fromIndex, toIndex).toList()
        for (i in (toIndex - 1) downTo fromIndex) {
            if (i in mediaItems.indices) mediaItems.removeAt(i)
        }
        val insertIndex = if (newIndex > fromIndex) newIndex - itemsToMove.size else newIndex
        mediaItems.addAll(insertIndex.coerceIn(0, mediaItems.size), itemsToMove)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        for (i in (toIndex - 1) downTo fromIndex) {
            if (i in mediaItems.indices) mediaItems.removeAt(i)
        }
    }

    override fun prepare() {}
    override fun getSeekBackIncrement(): Long = 10_000L
    override fun getSeekForwardIncrement(): Long = 10_000L
    override fun getMaxSeekToPreviousPosition(): Long = 3000L
    override fun stop() { actionDispatcher.pause() }
    override fun release() { scope.cancel() }
    override fun getDuration(): Long = playbackController.uiState.value.durationMs
    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = -1
    override fun getCurrentAdIndexInAdGroup(): Int = -1
    override fun getContentPosition(): Long = playbackController.uiState.value.positionMs
    override fun getContentBufferedPosition(): Long = playbackController.uiState.value.positionMs
    override fun getCurrentCues(): androidx.media3.common.text.CueGroup = androidx.media3.common.text.CueGroup(mutableListOf(), 0L)
    override fun getDeviceInfo(): androidx.media3.common.DeviceInfo = androidx.media3.common.DeviceInfo.UNKNOWN
    override fun getDeviceVolume(): Int = (playbackController.uiState.value.volume * 15).toInt()
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int) {}
    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun increaseDeviceVolume() {}
    override fun increaseDeviceVolume(flags: Int) {}
    override fun decreaseDeviceVolume() {}
    override fun decreaseDeviceVolume(flags: Int) {}
    override fun setDeviceMuted(muted: Boolean) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}

    // ========== Helpers ==========

    fun setPlaylistFromEntities(tracks: List<DownloadItemEntity>) {
        mediaItems.clear()
        mediaItems.addAll(tracks.map { it.toMediaItem() })
    }

    private fun DownloadItemEntity.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(outputPath)
            .setMediaMetadata(toMediaMetadata())
            .build()
    }

    private fun DownloadItemEntity.toMediaMetadata(): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    }

    private class BassTimeline(private val items: List<MediaItem>) : Timeline() {
        override fun getWindowCount(): Int = items.size.coerceAtLeast(1)
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            return window.apply {
                uid = items.getOrNull(windowIndex)?.mediaId ?: "empty"
                mediaItem = items.getOrElse(windowIndex) { MediaItem.EMPTY }
                isSeekable = true
            }
        }
        override fun getPeriodCount(): Int = items.size.coerceAtLeast(1)
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            return period.apply {
                uid = items.getOrNull(periodIndex)?.mediaId ?: "empty"
                windowIndex = periodIndex
            }
        }
        override fun getIndexOfPeriod(uid: Any): Int = items.indexOfFirst { it.mediaId == uid }
        override fun getUidOfPeriod(periodIndex: Int): Any = items.getOrNull(periodIndex)?.mediaId ?: "empty"
    }
}
