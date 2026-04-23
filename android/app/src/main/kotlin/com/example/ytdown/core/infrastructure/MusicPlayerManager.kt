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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val metadataService: MetadataService
) {
    private val _player = ExoPlayer.Builder(context).build()
    val player: Player get() = _player

    private val _currentTrack = MutableStateFlow<DownloadItemEntity?>(null)
    val currentTrack: StateFlow<DownloadItemEntity?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    init {
        _player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    suspend fun play(item: DownloadItemEntity) {
        val playableItem = resolvePlayableItem(item) ?: return
        
        _currentTrack.value = playableItem
        
        // Iniciar enriquecimento de metadados em background (LastFM/iTunes/Deezer)
        hydrateMetadata(playableItem)

        updatePlayerSource(playableItem)
        _player.prepare()
        _player.play()
    }

    private fun updatePlayerSource(item: DownloadItemEntity) {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(getArtworkUri(item))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(Uri.fromFile(File(item.filePath)))
            .setMediaMetadata(metadata)
            .build()

        _player.setMediaItem(mediaItem)
    }

    private fun hydrateMetadata(item: DownloadItemEntity) {
        if (!item.artistImageUrl.isNullOrEmpty() && !item.albumImageUrl.isNullOrEmpty()) return

        serviceScope.launch {
            val artMap = metadataService.getArtwork(item.artist, item.album, item.title)
            val updated = item.copy(
                artistImageUrl = artMap["artistArt"] ?: item.artistImageUrl,
                albumImageUrl = artMap["albumArt"] ?: item.albumImageUrl
            )
            
            if (updated != item) {
                downloadDao.upsert(updated)
                // Se ainda for a música atual, atualiza o estado da UI e do player
                if (_currentTrack.value?.id == item.id) {
                    _currentTrack.value = updated
                    // Opcional: Atualizar metadados do player sem reiniciar
                }
            }
        }
    }

    private fun getArtworkUri(item: DownloadItemEntity): Uri? {
        val path = item.albumImageUrl ?: item.artistImageUrl ?: item.thumbnailPath
        return path?.let { Uri.parse(it) }
    }

    private suspend fun resolvePlayableItem(item: DownloadItemEntity): DownloadItemEntity? {
        val file = File(item.filePath)
        if (file.exists()) return item

        val directory = file.parentFile ?: return null
        val expectedBase = file.nameWithoutExtension
        val recoveredFile = directory.listFiles()?.find { 
            it.name.startsWith(expectedBase) && it.isFile 
        }

        return if (recoveredFile != null) {
            val updated = item.copy(filePath = recoveredFile.absolutePath)
            downloadDao.upsert(updated)
            updated
        } else null
    }

    fun togglePlayPause() {
        if (_player.isPlaying) _player.pause() else _player.play()
    }

    fun next() = _player.seekToNext()
    fun previous() = _player.seekToPrevious()
    fun seekTo(positionMs: Long) = _player.seekTo(positionMs)
    fun stop() = _player.stop()
    fun release() = _player.release()
}
