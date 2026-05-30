package com.example.ytdown.core.infrastructure

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.example.ytdown.core.audio.*

private const val PREFS_NAME = "player_state"
private const val KEY_TRACK_ID = "last_track_id"
private const val KEY_POSITION_MS = "last_position_ms"

/**
 * MusicPlayerManager - Gerenciador de reprodução que agora usa PlaybackController como Single Source of Truth.
 * Mantido para compatibilidade com a UI existente.
 */
@Singleton
class MusicPlayerManager
@Inject
constructor(
        private val player: BassPlaybackEngine, // Motor BASS unificado
        private val controller: PlaybackController,
        private val downloadDao: DownloadDao,
        private val metadataService: MetadataService,
        @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MusicPlayerManager"
    }

    // Single Source of Truth - agora vem do PlaybackController
    val uiState: StateFlow<PlaybackUiState> = controller.uiState

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val currentTrack: StateFlow<DownloadItemEntity?> = controller.uiState
        .map { it.currentTrack }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val repeatMode: StateFlow<Int> = controller.uiState
        .map { it.repeatMode }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val isShuffleEnabled: StateFlow<Boolean> = controller.uiState
        .map { it.isShuffleEnabled }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)
        
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var positionSaveJob: Job? = null
    private var playlist = mutableListOf<DownloadItemEntity>()
    private var currentIndex = -1

    fun destroy() {
        positionSaveJob?.cancel()
        saveCurrentPositionNow()
        scope.cancel()
        player.stop()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        scope.launch {
            uiState.collect { state ->
                Log.d(TAG, "UI State changed: isPlaying=${state.isPlaying}, track=${state.currentTrack?.title}")
                if (state.isPlaying) startPositionSaveLoop() else stopPositionSaveLoop()
            }
        }

        // Registrar Noisy Receiver
        context.registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
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
        val trackId = uiState.value.currentTrack?.id ?: return
        val position = uiState.value.positionMs
        prefs.edit().putString(KEY_TRACK_ID, trackId).putLong(KEY_POSITION_MS, position).apply()
    }

    fun saveCurrentPositionNow() {
        val trackId = uiState.value.currentTrack?.id ?: return
        val position = uiState.value.positionMs
        prefs.edit()
                .putString(KEY_TRACK_ID, trackId)
                .putLong(KEY_POSITION_MS, position)
                .commit()
    }

    fun restoreLastPosition() {
        val savedTrackId = prefs.getString(KEY_TRACK_ID, null) ?: return
        val savedPosition = prefs.getLong(KEY_POSITION_MS, 0L)
        val currentId = uiState.value.currentTrack?.id

        if (currentId == savedTrackId && savedPosition > 0L) {
            player.seekTo(savedPosition)
        } else if (currentId == null && savedPosition > 0L) {
            scope.launch {
                val item = downloadDao.getById(savedTrackId) ?: return@launch
                val resolved = resolvePlayableItem(item) ?: return@launch
                player.play(resolved)
                player.pause()
                player.seekTo(savedPosition)
            }
        }
    }

    fun toggleRepeatMode() {
        Log.d(TAG, "toggleRepeatMode() called")
        val nextMode = (uiState.value.repeatMode + 1) % 3
        controller.updateRepeatMode(nextMode)
    }

    fun toggleShuffle() {
        Log.d(TAG, "toggleShuffle() called")
        val nextShuffle = !uiState.value.isShuffleEnabled
        controller.updateShuffle(nextShuffle)
    }

    fun playTrack(item: DownloadItemEntity) {
        playPlaylist(listOf(item), 0)
    }

    fun getAudioEngine(): BassPlaybackEngine = player
    
    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) {
        scope.launch {
            Log.d(TAG, "playPlaylist() called with ${items.size} items, startIndex=$startIndex")

            val validItems = items.mapNotNull { resolvePlayableItem(it) }
            if (validItems.isEmpty()) {
                Log.w(TAG, "No valid items in playlist")
                return@launch
            }

            playlist = validItems.toMutableList()
            currentIndex = startIndex.coerceIn(0, playlist.size - 1)

            // Usar PlaybackController (single source of truth) que:
            // 1. Alimenta o BassMediaSessionAdapter com a playlist
            // 2. Inicia o MediaPlaybackService (notificação, Bluetooth, Now Bar)
            // 3. Inicia o BASS engine
            controller.playPlaylist(playlist, currentIndex)

            hydrateArtworkIfMissing(playlist[currentIndex])
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
        if (!item.albumArtPath.isNullOrEmpty() && !item.artistArtPath.isNullOrEmpty()) return

        scope.launch(Dispatchers.IO) {
            val artwork =
                    metadataService.getArtwork(item.artist ?: "Unknown", item.album, item.title)

            if (artwork.isNotEmpty()) {
                val updated =
                        item.copy(
                                albumArtPath = artwork["albumArt"] ?: item.albumArtPath,
                                artistArtPath = artwork["artistArt"] ?: item.artistArtPath
                        )
                downloadDao.upsert(updated)

                if (uiState.value.currentTrack?.id == item.id) {
                    withContext(Dispatchers.Main) {
                        controller.updateTrack(updated)
                        updatePlayerMetadata(updated)
                    }
                }
            }
        }
    }

    private fun resolveArtworkUri(item: DownloadItemEntity): Uri? {
        val path = item.albumArtPath ?: item.albumArtPath
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http") || path.startsWith("content://")) return Uri.parse(path)
        return Uri.fromFile(File(path))
    }

    fun pause() {
        Log.d(TAG, "pause() called")
        player.pause()
    }
    
    fun resume() {
        Log.d(TAG, "resume() called")
        player.resume()
    }

    private fun updatePlayerMetadata(item: DownloadItemEntity) {
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = "UPDATE_METADATA"
        }
        context.startService(intent)
    }

    fun next() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playPlaylist(playlist, currentIndex)
    }
    fun previous() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        playPlaylist(playlist, currentIndex)
    }
    fun seekTo(position: Long) = player.seekTo(position)
}
