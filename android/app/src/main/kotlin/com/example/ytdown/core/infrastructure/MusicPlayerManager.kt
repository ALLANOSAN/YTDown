package com.example.ytdown.core.infrastructure

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "player_state"
private const val KEY_TRACK_ID = "last_track_id"
private const val KEY_POSITION_MS = "last_position_ms"

/**
 * Gerencia a reprodução de áudio com suporte a Shuffle, Repeat, Auto-Correção de Caminho,
 * Hidratação de Artes e Persistência de Posição de Reprodução.
 */
@Singleton
class MusicPlayerManager @Inject constructor(
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

    // Job de auto-save periódico enquanto tocando
    private var positionSaveJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
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
        })
    }

    // ── Persistência de posição ─────────────────────────────────────────────

    private fun startPositionSaveLoop() {
        positionSaveJob?.cancel()
        positionSaveJob = scope.launch {
            while (isActive) {
                saveCurrentPosition()
                delay(5_000) // salva a cada 5s enquanto tocando
            }
        }
    }

    private fun stopPositionSaveLoop() {
        positionSaveJob?.cancel()
        saveCurrentPosition() // salva imediatamente ao pausar/parar
    }

    private fun saveCurrentPosition() {
        val trackId = _currentTrack.value?.id ?: return
        val position = player.currentPosition
        prefs.edit()
            .putString(KEY_TRACK_ID, trackId)
            .putLong(KEY_POSITION_MS, position)
            .apply()
    }

    /** Chamado pelo Application.onStop — salva posição de forma síncrona (commit, não apply) */
    fun saveCurrentPositionNow() {
        val trackId = _currentTrack.value?.id ?: return
        val position = player.currentPosition
        prefs.edit()
            .putString(KEY_TRACK_ID, trackId)
            .putLong(KEY_POSITION_MS, position)
            .commit() // commit() é síncrono — garante escrita antes do processo morrer
    }

    /** Chamado ao abrir o player para restaurar a posição da última sessão. */
    fun restoreLastPosition() {
        val savedTrackId = prefs.getString(KEY_TRACK_ID, null) ?: return
        val savedPosition = prefs.getLong(KEY_POSITION_MS, 0L)
        val currentId = _currentTrack.value?.id

        if (currentId == savedTrackId && savedPosition > 0L) {
            player.seekTo(savedPosition)
        } else if (currentId == null && savedPosition > 0L) {
            // App foi fechado enquanto tocava — carrega a faixa e restaura
            scope.launch {
                val item = downloadDao.getById(savedTrackId) ?: return@launch
                val resolved = resolvePlayableItem(item) ?: return@launch
                _currentTrack.value = resolved
                player.setMediaItem(buildMediaItem(resolved))
                player.prepare()
                player.seekTo(savedPosition)
                // Não inicia play automaticamente — só posiciona
            }
        }
    }

    fun toggleRepeatMode() {
        var nextMode = Player.REPEAT_MODE_OFF
        if (player.repeatMode == Player.REPEAT_MODE_OFF) {
            nextMode = Player.REPEAT_MODE_ALL
        }
        if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            nextMode = Player.REPEAT_MODE_ONE
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
        scope.launch {
            val resolvedItem = resolvePlayableItem(item) ?: return@launch
            _currentTrack.value = resolvedItem
            
            val mediaItem = buildMediaItem(resolvedItem)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
            hydrateArtworkIfMissing(resolvedItem)
        }
    }

    fun getPlayer(): Player = player

    fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) {
        scope.launch {
            val validItems = items.mapNotNull { resolvePlayableItem(it) }
            if (validItems.isEmpty()) return@launch

            val mediaItems = validItems.map { buildMediaItem(it) }
            player.setMediaItems(mediaItems, startIndex, 0)
            player.prepare()
            player.play()
            
            validItems.getOrNull(startIndex)?.let { hydrateArtworkIfMissing(it) }
        }
    }

    /**
     * Lógica de Auto-Correção de Caminho (Migrado do Flutter _resolvePlayableItem).
     * Se o arquivo original sumiu, procura por um similar no mesmo diretório.
     */
    private suspend fun resolvePlayableItem(item: DownloadItemEntity): DownloadItemEntity? = withContext(Dispatchers.IO) {
        if (!item.exportedPath.isNullOrBlank()) return@withContext item

        val file = File(item.outputPath)
        if (file.exists()) return@withContext item

        val directory = file.parentFile ?: return@withContext null
        if (!directory.exists()) return@withContext null

        val expectedBaseName = file.nameWithoutExtension
        val foundFile = directory.listFiles()?.find {
            it.nameWithoutExtension.startsWith(expectedBaseName)
        }

        if (foundFile != null) {
            val updated = item.copy(
                outputPath = foundFile.absolutePath,
                format = foundFile.extension
            )
            downloadDao.upsert(updated)
            return@withContext updated
        }

        return@withContext null
    }

    /**
     * Busca capas faltantes em background enquanto a música toca (Migrado do Flutter _hydrateArtworkForTrack).
     */
    private fun hydrateArtworkIfMissing(item: DownloadItemEntity) {
        if (!item.albumImageUrl.isNullOrEmpty() && !item.artistImageUrl.isNullOrEmpty()) return

        scope.launch(Dispatchers.IO) {
            val artwork = metadataService.getArtwork(
                item.artist ?: "Unknown",
                item.album,
                item.title
            )
            
            if (artwork.isNotEmpty()) {
                val updated = item.copy(
                    albumImageUrl = artwork["albumArt"] ?: item.albumImageUrl,
                    artistImageUrl = artwork["artistArt"] ?: item.artistImageUrl
                )
                downloadDao.upsert(updated)
                
                // Se ainda for a música atual, atualiza o estado da UI
                if (_currentTrack.value?.id == item.id) {
                    _currentTrack.value = updated
                }
            }
        }
    }

    private fun buildMediaItem(item: DownloadItemEntity): MediaItem {
        val uri = item.exportedPath?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: item.outputPath.takeIf { it.isNotBlank() }?.let { Uri.fromFile(File(it)) }
            ?: Uri.EMPTY

        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .setArtworkUri(item.thumbnailPath?.let { Uri.parse(it) })
                    .build()
            ).build()
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun next() = player.seekToNext()
    fun previous() = player.seekToPrevious()
    fun seekTo(position: Long) = player.seekTo(position)
}
