package com.example.ytdown

import android.content.Context
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.infrastructure.MediaScanner
import com.example.ytdown.core.infrastructure.MimeTypeResolver
import com.example.ytdown.core.business.MediaInfoParser
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.utils.LocalLogger
import com.example.ytdown.utils.LruCache
import javax.inject.Inject
import javax.inject.Singleton

class MetadataTools(val scanner: MediaScanner, val resolver: MimeTypeResolver)

@Singleton
class DownloadMetadataManager @Inject constructor(
    private val tools: MetadataTools,
    private val parser: MediaInfoParser,
    private val ytDlp: YtDlpWrapper
) {
    private val infoCache = LruCache<String, VideoInfoJson>(50)

    fun fetchVideoInfo(context: Context, url: VideoUrl): VideoInfoJson {
        infoCache.get(url.value)?.let { return it }

        val result = try {
            PythonBridge.invokePythonJson(
                "fetch_video_info",
                url.value,
                PythonBridge.appFilesDirPath(context),
            )
        } catch (e: Exception) {
            LocalLogger.error("Erro ao buscar info: ${e.message}", e)
            """{"success": false, "error": "${e.message}"}"""
        }

        return VideoInfoJson(result).also { infoCache.put(url.value, it) }
    }

    fun parseEntries(infoJson: VideoInfoJson): List<VideoPreviewItem> = parser.parseEntries(infoJson)

    fun guessArtistFromTitle(title: MediaTitle): String = parser.guessArtistFromTitle(title)

    fun guessAlbumFromTitle(title: MediaTitle): String = parser.guessAlbumFromTitle(title)

    fun rewriteMetadata(
        path: FilePath,
        metadata: MediaMetadata
    ): ExitCode {
        val result = ytDlp.rewriteMetadata(
            filePath = path.value,
            title = metadata.title.value,
            artist = metadata.artist.value,
            album = metadata.album.value,
            artworkUrl = null
        )

        val mime = tools.resolver.fromPath(path)
        tools.scanner.scanSync(path, mime)
        
        return result
    }
}
