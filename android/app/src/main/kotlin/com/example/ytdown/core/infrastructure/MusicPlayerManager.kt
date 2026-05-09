package com.example.ytdown.core.infrastructure

import android.content.Context
import android.net.Uri
import android.content.Intent
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "player_state"
private const val KEY_TRACK_ID = "last_track_id"
private const val KEY_POSITION_MS = "last_position_ms"

@Singleton
class MusicPlayerManager
@Inject
constructor(
        private val player: ExoPlayer,
        private val downloadDao: DownloadDao,
        private val metadataService: MetadataService,
        @param:ApplicationContext private val context: Context
) {
    private val _currentTrack = MutableStateFlow<DownloadItemEntity?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var positionSaveJob: Job? = null

    // FIX #16: Lifecycle methods for proper scope cancellation
    /** Call from Activity/Fragment onCreate or when the manager becomes active. */
    fun onCreate() {
        // No-op for now; exists as a lifecycle anchor for future init logic
    }

    /** Call from Activity/Fragment onDestroy to cancel all coroutines and save state. */
    fun onDestroy() {
        destroy()
    }

    /** Cancel the CoroutineScope. Safe to call multiple times. */
    fun destroy() {
        positionSaveJob?.cancel()
        saveCurrentPositionNow()
        scope.cancel()
    }

    init {
        player.addListener(
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaId?.let { id ->
                            scope.launch {
                                val item = downloadDao.getById(id)
                                _currentTrack.value = item
                                item?.let { hydrateArtworkIfMissing(it) }
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) startPositionSaveLoop() else stopPositionSaveLoop()
                    }
                }
        )
    }

    private fun startPositionSaveLoop() {
        positionSaveJob?.cancel()
        positionSaveJob =
                scope.launch {
                    while (isActive) {
                        saveCurrentPosition()
                        delay(5_000)
                    }
                }
    }

    private fun stopPositionSaveLoop() {
        positionSaveJob?.cancel()
        saveCurrentPosition()
    }

    private fun saveCurrentPosition() {
        val trackId = _currentTrack.value?.id ?: return
        val position = player.currentPosition
        prefs.edit().putString(KEY_TRACK_ID, trackId).putLong(KEY_POSITION_MS, position).apply()
    }

    fun saveCurrentPositionNow() {
        val trackId = _currentTrack.value?.id ?: return
        val position = player.currentPosition
        prefs.edit()
                .putString(KEY_TRACK_ID, trackId)
                .putLong(KEY_POSITION_MS, position)
                .commit()
    }

    fun restoreLastPosition() {
        val savedTrackId = prefs.getString(KEY_TRACK_ID, null) ?: return
        val savedPosition = prefs.getLong(KEY_POSITION_MS, 0L)
        val currentId = _currentTrack.value?.id

        if (currentId == savedTrackId && savedPosition > 0L) {
            player.seekTo(savedPosition)
        } else if (currentId == null && savedPosition > 0L) {
            scope.launch {
                val item = downloadDao.getById(savedTrackId) ?: return@launch
                val resolved = resolvePlayableItem(item) ?: return@launch
                _currentTrack.value = resolved
                player.setMediaItem(buildMediaItem(resolved))
                player.prepare()
                player.seekTo(savedPosition)
            }
        }
    }

    // FIX #21: Replace if-chain with when expression
    fun toggleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun toggleShuffle() {
        val nextShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = nextShuffle
        _isShuffleEnabled.value = nextShuffle
    }

    fun playTrack(item: DownloadItemEntity) {
        playPlaylist(listOf(item), 0)
    }

    fun getPlayer(): Player = player
    fun getAudioSessionId(): Int = player.audioSessionId
    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) {
        scope.launch {
            val intent = Intent(context, MediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            kotlinx.coroutines.yield()

            val validItems = items.mapNotNull { resolvePlayableItem(it) }
            if (validItems.isEmpty()) return@launch

            val mediaItems = validItems.map { buildMediaItem(it) }

            player.clearMediaItems()
            player.setMediaItems(mediaItems)
            player.seekTo(startIndex, 0)
            player.prepare()
            player.playWhenReady = true

            validItems.getOrNull(startIndex)?.let { hydrateArtworkIfMissing(it) }
        }
    }

    private suspend fun resolvePlayableItem(item: DownloadItemEntity): DownloadItemEntity? =
            withContext(Dispatchers.IO) {
                if (!item.exportedPath.isNullOrBlank()) return@withContext item
                if (item.outputPath.startsWith("content://")) return@withContext item

                val file = File(item.outputPath)
                if (file.exists()) return@withContext item

                val directory = file.parentFile ?: return@withContext null
                if (!directory.exists()) return@withContext null

                val expectedBaseName = file.nameWithoutExtension
                val foundFile =
                        directory.listFiles()?.find {
                            it.nameWithoutExtension.startsWith(expectedBaseName)
                        }

                if (foundFile != null) {
                    val updated =
                            item.copy(
                                    outputPath = foundFile.absolutePath,
                                    format = foundFile.extension
                            )
                    downloadDao.upsert(updated)
                    return@withContext updated
                }

                return@withContext null
            }

    private fun hydrateArtworkIfMissing(item: DownloadItemEntity) {
        if (!item.albumImageUrl.isNullOrEmpty() && !item.artistImageUrl.isNullOrEmpty()) return

        scope.launch(Dispatchers.IO) {
            val artwork =
                    metadataService.getArtwork(item.artist ?: "Unknown", item.album, item.title)

            if (artwork.isNotEmpty()) {
                val updated =
                        item.copy(
                                albumImageUrl = artwork["albumArt"] ?: item.albumImageUrl,
                                artistImageUrl = artwork["artistArt"] ?: item.artistImageUrl
                        )
                downloadDao.upsert(updated)

                if (_currentTrack.value?.id == item.id) {
                    withContext(Dispatchers.Main) {
                        _currentTrack.value = updated
                        updatePlayerMetadata(updated)
                    }
                }
            }
        }
    }

    private fun updatePlayerMetadata(item: DownloadItemEntity) {
        val index = player.currentMediaItemIndex
        val currentMediaItem = player.currentMediaItem ?: return

        if (currentMediaItem.mediaId == item.id) {
            val updatedMetadata =
                    currentMediaItem
                            .mediaMetadata
                            .buildUpon()
                            .setArtworkUri(resolveArtworkUri(item))
                            .build()

            player.replaceMediaItem(
                    index,
                    currentMediaItem.buildUpon().setMediaMetadata(updatedMetadata).build()
            )
        }
    }

    private fun buildMediaItem(item: DownloadItemEntity): MediaItem {
        val uri =
                item.exportedPath?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                        ?: item.outputPath.takeIf { it.isNotBlank() }?.let {
                            if (it.startsWith("content://")) Uri.parse(it)
                            else Uri.fromFile(File(it))
                        }
                                ?: Uri.EMPTY

        return MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(uri)
                .setMediaMetadata(
                        MediaMetadata.Builder()
                                .setTitle(item.title)
                                .setArtist(item.artist)
                                .setAlbumTitle(item.album)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setArtworkUri(resolveArtworkUri(item))
                                .build()
                )
                .build()
    }

    private fun resolveArtworkUri(item: DownloadItemEntity): Uri? {
        val path = item.albumImageUrl ?: item.thumbnailPath
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http") || path.startsWith("content://")) return Uri.parse(path)
        return Uri.fromFile(File(path))
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun next() = player.seekToNext()
    fun previous() = player.seekToPrevious()
    fun seekTo(position: Long) = player.seekTo(position)
}
