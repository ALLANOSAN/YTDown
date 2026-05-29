package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.AlbumName
import com.example.ytdown.core.domain.ArtistName
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.MusicBrainzService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BatchMetadataFixWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseService: DatabaseService,
    private val musicBrainzService: MusicBrainzService,
    private val downloadMetadataManager: DownloadMetadataManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songs = databaseService.getLibraryAudios()

        for (song in songs) {
            try {
                val artistName = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown"
                // Busca tags pelo nome do artista (não MBID)
                val tags = musicBrainzService.getArtistTagsByName(artistName)
                val artworkUrl = song.albumArtPath ?: song.albumArtPath

                val updatedSong = song.copy(artistArtPath = artworkUrl)
                databaseService.updateDownload(updatedSong)

                val targetPath = updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath

                downloadMetadataManager.rewriteMetadata(
                    path = FilePath(targetPath),
                    metadata = MediaMetadata(
                        MediaTitle(updatedSong.title),
                        ArtistName(updatedSong.artist.orEmpty()),
                        AlbumName(updatedSong.album.orEmpty())
                    ),
                    artworkUrl = artworkUrl
                )
            } catch (e: Exception) {
                android.util.Log.e("BatchMetadataFixWorker", "Erro ao corrigir ${song.title}: ${e.message}")
            }
        }
        Result.success()
    }
}