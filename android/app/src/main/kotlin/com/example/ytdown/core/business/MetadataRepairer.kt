package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.services.DatabaseService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataRepairer @Inject constructor(
    private val databaseService: DatabaseService,
    private val metadataManager: DownloadMetadataManager,
    private val ytDlp: YtDlpWrapper
) {
    suspend fun repairAll(
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Int, Int> {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) return Triple(0, 0, 0)

        var repaired = 0
        var failed = 0
        var skipped = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Processando: ${item.title}")

            if (item.outputPath.isBlank() || !File(item.outputPath).exists()) {
                failed++
                continue
            }

            // Pular arquivos que ja tem tags completas
            val hasTitle = !item.title.isNullOrBlank() &&
                !item.title.equals("Unknown", ignoreCase = true) &&
                !item.title.equals("Desconhecido", ignoreCase = true)
            val hasArtist = !item.artist.isNullOrBlank() &&
                !item.artist.equals("Unknown", ignoreCase = true) &&
                !item.artist.equals("Desconhecido", ignoreCase = true)
            val hasAlbum = !item.album.isNullOrBlank() &&
                !item.album.equals("Unknown Album", ignoreCase = true) &&
                !item.album.equals("Álbum Desconhecido", ignoreCase = true)
            if (hasTitle && hasArtist && hasAlbum) {
                android.util.Log.d("MetadataRepairer", "Pulando ${item.title} — ja tem tags completas")
                skipped++
                continue
            }

            var finalTitle = item.title.trim()
            var finalArtist = item.artist?.trim().orEmpty()
            var finalAlbum = item.album?.trim().orEmpty()

            val enriched = ytDlp.fetchMetadataFromSource(finalArtist, finalTitle)
            if (enriched != null) {
                finalTitle = enriched.optString("title", finalTitle)
                finalArtist = enriched.optString("artist", finalArtist)
                finalAlbum = enriched.optString("album", finalAlbum)
            }

            val result = metadataManager.rewriteMetadata(
                path = FilePath(item.outputPath),
                metadata = MediaMetadata(MediaTitle(finalTitle), ArtistName(finalArtist), AlbumName(finalAlbum)),
                artworkUrl = null
            )

            if (result.isSuccess()) repaired++ else failed++
        }
        return Triple(repaired, failed, skipped)
    }
}
