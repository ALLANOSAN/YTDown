package com.example.ytdown.core.audio

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.example.ytdown.core.domain.DownloadItemEntity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BassMediaSessionAdapter - Conecta o BASS engine ao Media3 Player interface.
 *
 * Usa SimpleBasePlayer (Media3 1.5+) que é a classe base recomendada para
 * players customizados. Ela gerencia thread safety, notificação de listeners
 * e state diffing automaticamente.
 *
 * Para Android 16 Live Island (Now Bar) + MediaSession + Notification.
 */
@Singleton
class BassMediaSessionAdapter @Inject constructor(
    private val playbackController: PlaybackController,
    private val actionDispatcher: PlaybackActionDispatcher
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private var mediaItems = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private var playWhenReady = false
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Observar mudanças de estado do BASS e invalidar estado do Media3
        scope.launch {
            playbackController.uiState.collect { state ->
                playWhenReady = state.isPlaying
                // Sincronizar currentIndex com o PlaybackController
                // (playNext/Previous agora bypassam MediaController para evitar loop)
                state.currentTrack?.let { track ->
                    val idx = mediaItems.indexOfFirst { it.mediaId == track.id }
                    if (idx >= 0) currentIndex = idx
                }
                invalidateState()
            }
        }
    }

    // ── SimpleBasePlayer contract ──────────────────────────────────────────

    override fun getState(): State {
        val state = playbackController.uiState.value
        val duration = if (state.durationMs > 0) state.durationMs else C.TIME_UNSET
        val position = state.positionMs

        val playbackState = when {
            state.isBuffering -> STATE_BUFFERING
            state.currentTrack != null -> STATE_READY
            else -> STATE_IDLE
        }

        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs(position)
            .setPlaylist(mediaItems.mapIndexed { i, item ->
                MediaItemData.Builder(item.mediaId.ifEmpty { i.toString() })
                    .setMediaItem(item)
                    .setDurationUs(if (i == currentIndex && duration != C.TIME_UNSET) duration * 1000L else C.TIME_UNSET)
                    .setIsPlaceholder(false)
                    .build()
            })
            .build()
    }

    // ── Command handlers ───────────────────────────────────────────────────

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            actionDispatcher.play()
            this.playWhenReady = true
            startPositionPolling()
        } else {
            actionDispatcher.pause()
            this.playWhenReady = false
            stopPositionPolling()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
        this.currentIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val insertAt = index.coerceIn(0, this.mediaItems.size)
        this.mediaItems.addAll(insertAt, mediaItems)
        if (insertAt <= currentIndex) currentIndex += mediaItems.size
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val range = fromIndex.coerceAtLeast(0)..toIndex.coerceAtMost(this.mediaItems.size)
        this.mediaItems.subList(range.first, range.last).clear()
        when {
            this.mediaItems.isEmpty() -> { currentIndex = 0 }
            currentIndex >= this.mediaItems.size -> currentIndex = this.mediaItems.size - 1
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != currentIndex && mediaItemIndex in mediaItems.indices) {
            currentIndex = mediaItemIndex
            actionDispatcher.next()
        } else {
            actionDispatcher.seekTo(positionMs.coerceAtLeast(0L))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        actionDispatcher.toggleRepeatMode()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        actionDispatcher.toggleShuffle()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        actionDispatcher.pause()
        playWhenReady = false
        stopPositionPolling()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopPositionPolling()
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        // BASS não suporta change speed/pitch via Media3
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        playbackController.updateVolume(volume)
        return Futures.immediateVoidFuture()
    }

    // ── Position polling ───────────────────────────────────────────────────

    private fun startPositionPolling() {
        stopPositionPolling()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(500)
                invalidateState()
            }
        }
    }

    private fun stopPositionPolling() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // ── Playlist management ────────────────────────────────────────────────

    /**
     * Define a playlist a partir de DownloadItemEntity
     */
    fun setPlaylistFromEntities(tracks: List<DownloadItemEntity>) {
        mediaItems.clear()
        mediaItems.addAll(tracks.map { it.toMediaItem() })
        invalidateState()
    }

    private fun DownloadItemEntity.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        // Adicionar artwork se disponível
        albumArtPath?.let { path ->
            try {
                val uri = if (path.startsWith("http") || path.startsWith("content://")) {
                    android.net.Uri.parse(path)
                } else {
                    android.net.Uri.fromFile(java.io.File(path))
                }
                metadataBuilder.setArtworkUri(uri)
            } catch (e: Exception) {
                android.util.Log.w("BassAdapter", "Failed to set artwork URI: ${e.message}")
            }
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(outputPath)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    // ── Available commands ─────────────────────────────────────────────────

    companion object {
        private val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_RELEASE,
            ).build()
    }
}
