package com.example.ytdown

import android.content.Context
import android.net.Uri
import com.example.ytdown.core.business.MediaInfoParser
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.core.infrastructure.MediaScanner
import com.example.ytdown.core.infrastructure.MimeTypeResolver
import com.example.ytdown.services.StorageService
import com.example.ytdown.utils.MemoryLruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MetadataTools(val scanner: MediaScanner, val resolver: MimeTypeResolver)

@Singleton
class DownloadMetadataManager
@Inject
constructor(
        private val tools: MetadataTools,
        private val parser: MediaInfoParser,
        private val ytDlp: YtDlpWrapper,
        private val storageService: StorageService,
        private val orchestrator: BinaryOrchestrator,
        @param:ApplicationContext private val context: Context
) {
    private val infoCache = MemoryLruCache<String, VideoInfoJson>(50)

    fun fetchVideoInfo(url: VideoUrl): VideoInfoJson {
        val cached = infoCache.get(url.value)
        if (cached != null) return cached

        val json =
                try {
                    ytDlp.fetchVideoInfo(url.value, orchestrator.getAppFilesDir())
                } catch (e: Exception) {
                    throw e
                }

        if (!json.optBoolean("success", false)) {
            val errorMessage = json.optString("error", "Falha ao buscar informações do vídeo")
            throw java.lang.IllegalStateException(errorMessage)
        }

        val videoInfo = VideoInfoJson(json.toString())
        infoCache.put(url.value, videoInfo)
        return videoInfo
    }

    fun parseEntries(infoJson: VideoInfoJson): List<VideoPreviewItem> =
            parser.parseEntries(infoJson)

    fun isPlaylist(infoJson: VideoInfoJson): Boolean {
        val json = JSONObject(infoJson.value)
        val data = json.optJSONObject("data")

        if (json.optBoolean("is_playlist", false)) return true
        if (json.optString("_type").equals("playlist", ignoreCase = true)) return true
        if (data != null) {
            if (data.optBoolean("is_playlist", false)) return true
            if (data.optString("_type").equals("playlist", ignoreCase = true)) return true
            val entries = data.optJSONArray("entries")
            if (entries != null && entries.length() > 1) return true
        }

        val rootEntries = json.optJSONArray("entries")
        if (rootEntries != null && rootEntries.length() > 1) return true

        return false
    }

    fun guessArtistFromTitle(title: String): String? = parser.guessArtistFromTitle(title)

    fun guessAlbumFromTitle(title: String): String? = parser.guessAlbumFromTitle(title)

    suspend fun rewriteMetadata(
            path: FilePath,
            metadata: MediaMetadata,
            exportedPath: String? = null,
            artworkUrl: String? = null
    ): ExitCode {
        val target = exportedPath?.takeIf { it.isNotBlank() } ?: path.value
        val resolvedArtworkUrl = resolveArtworkUrl(artworkUrl)

        // ✅ FIX: ytDlp.rewriteMetadata() é bloqueante (Chaquo Python).
        // Sem withContext(IO) rodava na Main thread e causava ANR → app fechava ao salvar.
        return withContext(Dispatchers.IO) {
            try {
                if (target.startsWith("content://")) {
                    return@withContext rewriteMetadataOnContentUri(target, metadata, path, resolvedArtworkUrl)
                }

                val result =
                        ytDlp.rewriteMetadata(
                                filePath = target,
                                title = metadata.title.value,
                                artist = metadata.artist.value,
                                album = metadata.album.value,
                                artworkUrl = resolvedArtworkUrl
                        )

                if (result.isSuccess()) {
                    val mime = tools.resolver.fromPath(FilePath(target))
                    tools.scanner.scanSync(FilePath(target), mime)
                }
                result
            } finally {
                if (resolvedArtworkUrl != artworkUrl) {
                    File(resolvedArtworkUrl ?: "").takeIf { it.exists() }?.delete()
                }
            }
        }
    }

    private fun resolveArtworkUrl(artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) return null
        if (!artworkUrl.startsWith("content://")) return artworkUrl

        val uri = Uri.parse(artworkUrl)
        var extension = ".jpg"
        when (context.contentResolver.getType(uri)) {
            "image/png" -> extension = ".png"
            "image/webp" -> extension = ".webp"
        }
        val tempArtworkFile =
                File(context.cacheDir, "ytdown-artwork-${System.currentTimeMillis()}$extension")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempArtworkFile.outputStream().use { output -> input.copyTo(output) }
            }
                    ?: return null
            tempArtworkFile.absolutePath
        } catch (e: Exception) {
            tempArtworkFile.delete()
            null
        }
    }

    private suspend fun rewriteMetadataOnContentUri(
            exportedUri: String,
            metadata: MediaMetadata,
            originalPath: FilePath,
            artworkUrl: String?
    ): ExitCode {
        val extension = originalPath.value.substringAfterLast('.', "mp3")
        val tempFile =
                File(context.cacheDir, "ytdown-metadata-${System.currentTimeMillis()}.$extension")

        try {
            context.contentResolver.openInputStream(Uri.parse(exportedUri))?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
                    ?: return ExitCode(1)

            val result =
                    ytDlp.rewriteMetadata(
                            filePath = tempFile.absolutePath,
                            title = metadata.title.value,
                            artist = metadata.artist.value,
                            album = metadata.album.value,
                            artworkUrl = artworkUrl
                    )

            if (!result.isSuccess()) return result

            val synced =
                    storageService.syncEditedExportedFile(
                            context,
                            tempFile.absolutePath,
                            exportedUri
                    )
            if (!synced) return ExitCode(1)

            return result
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
