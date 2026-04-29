package com.example.ytdown.core.infrastructure

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerencia a reprodução de áudio com suporte a Shuffle, Repeat, Auto-Correção de Caminho e Hidratação de Artes.
 * Migrado do Flutter (player_service.dart).
 */
@Singleton
class MusicPlayerManager @Inject constructor(
    private val player: ExoPlayer,
    private val downloadDao: DownloadDao,
    private val metadataService: MetadataService
) {
    private val _currentTrack = MutableStateFlow<DownloadItemEntity?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        })
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
