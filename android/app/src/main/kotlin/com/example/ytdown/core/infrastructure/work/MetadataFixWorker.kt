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
import com.example.ytdown.utils.LocalLogger

@HiltWorker
class MetadataFixWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseService: DatabaseService,
    private val musicBrainzService: MusicBrainzService,
    private val downloadMetadataManager: DownloadMetadataManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString("SONG_ID") ?: return@withContext Result.failure()
        val allSongs = databaseService.getAllDownloads()
        val song = allSongs.find { it.id == songId } ?: return@withContext Result.failure()

        try {
            val artistName = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown"
            val tags = musicBrainzService.getArtistTagsByName(artistName)
            val genre = tags.firstOrNull()
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
            Result.success()
        } catch (e: Exception) {
            LocalLogger.error("Erro: ${e.message}", tag = "MetadataFixWorker")
            Result.retry()
        }
    }
}