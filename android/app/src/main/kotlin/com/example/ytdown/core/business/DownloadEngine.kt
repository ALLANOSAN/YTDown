package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.utils.MetadataUtils
import java.io.File

class DownloadEngine(
    private val ytDlp: YtDlpWrapper,
    private val metadataManager: DownloadMetadataManager
) {
    /**
     * Realiza o download e injeta os metadados (tags ID3/MP4).
     * Usa as lógicas de limpeza e adivinhação do MetadataUtils.
     */
    suspend fun downloadAndTag(
        url: VideoUrl,
        outputDir: File,
        metadata: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): ExitCode {
        // Limpa os nomes antes de enviar para o download
        val finalTitle = MetadataUtils.normalizeMetadataText(metadata.title.value)
        var finalArtist = metadata.artist.value
        if (MetadataUtils.isUnknownMetadata(metadata.artist.value)) {
            finalArtist = MetadataUtils.guessArtistFromTitle(metadata.title.value) ?: "Desconhecido"
        }
        
        val cleanedMeta = metadata.copy(
            title = MediaTitle(finalTitle),
            artist = ArtistName(finalArtist)
        )

        // O YtDlpWrapper (via Chaquopy) agora recebe os metadados diretamente
        val result = ytDlp.downloadVideo(
            url = url,
            outputDir = outputDir,
            options = options,
            metadata = cleanedMeta,
            artworkUrl = artworkUrl,
            onProgress = onProgress
        )
        
        // Se o download foi bem sucedido, garantimos a regravação fina das tags
        if (result.isSuccess()) {
            metadataManager.rewriteMetadata(FilePath(outputDir.absolutePath), cleanedMeta, artworkUrl = artworkUrl)
        }
        return result
    }
}
