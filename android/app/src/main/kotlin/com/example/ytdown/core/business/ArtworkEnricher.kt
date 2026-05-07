package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.services.ArtworkManager
import com.example.ytdown.services.DatabaseService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkEnricher @Inject constructor(
    private val databaseService: DatabaseService,
    private val artworkManager: ArtworkManager,
    private val metadataManager: DownloadMetadataManager
) {
    suspend fun enrichAll(
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Int, Int> {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) return Triple(0, 0, 0)

        var updated = 0
        var failed = 0
        var skipped = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Enriquecendo: ${item.title}")

            if (item.outputPath.isBlank() || !File(item.outputPath).exists()) {
                failed++
                continue
            }

            val resolution = resolveArtworkForItem(item)
            val artworkUrl = resolution.artworkUrl
            if (artworkUrl.isNullOrBlank()) {
                skipped++
                continue
            }

            val result = metadataManager.rewriteMetadata(
                path = FilePath(item.outputPath),
                metadata = MediaMetadata(
                    MediaTitle(item.title.trim()),
                    ArtistName(item.artist?.trim().orEmpty()),
                    AlbumName(item.album?.trim().orEmpty())
                ),
                artworkUrl = artworkUrl
            )

            if (result.isSuccess()) {
                val updatedItem = item.copy(
                    artistImageUrl = resolution.artistImageUrl ?: item.artistImageUrl,
                    albumImageUrl = resolution.albumImageUrl ?: item.albumImageUrl
                )
                databaseService.updateDownload(updatedItem)
                updated++
            } else {
                failed++
            }
        }
        return Triple(updated, failed, skipped)
    }

    private suspend fun resolveArtworkForItem(item: DownloadItemEntity): ArtworkResolution {
        val artist = item.artist?.trim().orEmpty()
        val album = item.album?.trim().takeIf { !it.isNullOrBlank() } ?: "YTDown"
        val title = item.title.trim()

        val artistImage = if (artist.isNotBlank()) artworkManager.getArtistImage(artist) else null
        val albumImage = if (artist.isNotBlank() && album != "YTDown") artworkManager.getAlbumCover(artist, album) else null
        val trackImage = if (artist.isNotBlank() && title.isNotBlank()) artworkManager.getTrackCover(artist, title) else null

        val finalArtwork = artistImage ?: albumImage ?: trackImage
        return ArtworkResolution(finalArtwork, artistImage, albumImage ?: trackImage)
    }

    private data class ArtworkResolution(
        val artworkUrl: String?,
        val artistImageUrl: String?,
        val albumImageUrl: String?
    )
}
