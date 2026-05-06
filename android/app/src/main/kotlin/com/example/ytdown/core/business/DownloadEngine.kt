package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.utils.MetadataUtils
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ytdown.services.ObservabilityService

class DownloadEngine(
    private val ytDlp: YtDlpWrapper,
    private val metadataManager: DownloadMetadataManager,
    private val observabilityService: ObservabilityService
) {
    suspend fun downloadAndTag(
        url: VideoUrl,
        outputDir: File,
        metadata: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val finalTitle = MetadataUtils.normalizeMetadataText(metadata.title.value)
            var finalArtist = metadata.artist.value
            if (MetadataUtils.isUnknownMetadata(metadata.artist.value)) {
                finalArtist = MetadataUtils.guessArtistFromTitle(metadata.title.value) ?: "Desconhecido"
            }

            val cleanedMeta = metadata.copy(
                title = MediaTitle(finalTitle),
                artist = ArtistName(finalArtist)
            )

            val result = ytDlp.downloadVideo(
                url = url,
                outputDir = outputDir,
                options = options,
                metadata = cleanedMeta,
                artworkUrl = artworkUrl,
                onProgress = onProgress
            )

            if (result.exitCode.isSuccess() && result.outputPath != null) {
                metadataManager.rewriteMetadata(
                    FilePath(result.outputPath),
                    cleanedMeta,
                    artworkUrl = artworkUrl
                )
            }
            result
        } catch (e: Exception) {
            observabilityService.trackError("DownloadEngine", "Falha catastrófica no download de ${url.value}", e)
            DownloadResult(ExitCode(1))
        }
    }
}
