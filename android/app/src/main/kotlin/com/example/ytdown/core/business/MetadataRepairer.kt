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
    ): Pair<Int, Int> {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) return 0 to 0

        var repaired = 0
        var failed = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Processando: ${item.title}")

            if (item.outputPath.isBlank() || !File(item.outputPath).exists()) {
                failed++
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
        return repaired to failed
    }
}
