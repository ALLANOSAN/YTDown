package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.domain.*
import com.example.ytdown.services.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BatchMetadataFixWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseService: DatabaseService,
    private val metalArchivesService: MetalArchivesService,
    private val lyricsService: LyricsService,
    private val downloadMetadataManager: DownloadMetadataManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songs = databaseService.getLibraryAudios()
        
        for (song in songs) {
            try {
                val artistName = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown"
                val response = metalArchivesService.getBandDetails(artistName)
                
                if (response.success) {
                    val artworkUrl = response.image_url ?: song.albumImageUrl ?: song.thumbnailPath
                    val lyricsRes = lyricsService.getLyrics(artistName, song.title)
                    val lyrics = lyricsRes?.syncedLyrics ?: lyricsRes?.plainLyrics

                    val updatedSong = song.copy(artistImageUrl = artworkUrl)
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
                }
            } catch (e: Exception) {
                android.util.Log.e("BatchMetadataFixWorker", "Erro ao corrigir ${song.title}: ${e.message}")
                // Continua para a próxima música em caso de erro
            }
        }
        Result.success()
    }
}
