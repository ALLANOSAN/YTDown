package com.example.ytdown.core.business

import com.chaquo.python.Python
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.core.infrastructure.PythonEnvironment
import com.example.ytdown.services.ObservabilityService
import java.io.File
import org.json.JSONObject

data class DownloadResult(val exitCode: ExitCode, val outputPath: String? = null)

class YtDlpWrapper(
        private val env: PythonEnvironment,
        private val binaryOrchestrator: BinaryOrchestrator,
        private val observabilityService: ObservabilityService
) {
    // FIX #7: Volatile cancelled flag for coroutine cancellation support
    @Volatile
    private var isCancelled = false

    /** Cancel any in-progress download. */
    fun cancel() {
        isCancelled = true
    }

    /** Reset cancellation state (e.g. before starting a new download). */
    fun resetCancellation() {
        isCancelled = false
    }
    /**
     * Executa o download de vídeo/áudio usando yt-dlp via Chaquopy. Retorna DownloadResult com
     * ExitCode e o caminho real do arquivo gerado.
     */
    fun downloadVideo(
            url: VideoUrl,
            outputDir: File,
            options: DownloadOptions,
            metadata: MediaMetadata? = null,
            artworkUrl: String? = null,
            onProgress: ((Int) -> Unit)? = null
    ): DownloadResult {
        // FIX #7: Check for early cancellation before starting
        if (isCancelled || Thread.currentThread().isInterrupted) {
            observabilityService.trackError("YtDlpWrapper", "Download cancelled before start", metadata = mapOf("url" to url.value))
            return DownloadResult(exitCode = ExitCode(1))
        }
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")

            binaryOrchestrator.setupNativeBinaries()
            val outputPath = File(outputDir, "%(title)s.%(ext)s").absolutePath

            val resultJson =
                    module.callAttr(
                                    "download_video",
                                    url.value,
                                    outputPath,
                                    options.type.value,
                                    options.quality,
                                    binaryOrchestrator.getNativeLibDir(),
                                    binaryOrchestrator.getAppFilesDir(),
                                    metadata?.artist?.value,
                                    metadata?.album?.value,
                                    artworkUrl,
                                    options.format
                            )
                            .toString()

            val result = JSONObject(resultJson)
            val success = result.optBoolean("success", false)
            val filename = result.optString("filename", "").takeIf { it.isNotBlank() }

            if (!success) {
                observabilityService.trackError("YtDlpWrapper", "Python download failed", metadata = mapOf("error" to result.optString("error", "Unknown"), "url" to url.value))
            }

            DownloadResult(
                    exitCode = if (success) ExitCode(0) else ExitCode(1),
                    outputPath = filename
            )
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Fatal error during download", e, mapOf("url" to url.value))
            DownloadResult(exitCode = ExitCode(1))
        }
    }

    fun fetchVideoInfo(url: String, appFilesDir: String): JSONObject {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")
            val resultJson = module.callAttr("fetch_video_info", url, appFilesDir).toString()
            JSONObject(resultJson)
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Error fetching video info", e, mapOf("url" to url))
            JSONObject().put("success", false).put("error", e.message)
        }
    }

    fun rewriteMetadata(
            filePath: String,
            title: String,
            artist: String?,
            album: String?,
            artworkUrl: String?
    ): ExitCode {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")

            val resultJson =
                    module.callAttr("rewrite_file_metadata", filePath, title, artist, album, artworkUrl)
                            .toString()

            val result = JSONObject(resultJson)
            var exit = ExitCode(1)
            val success = result.optBoolean("success", false)
            if (success) {
                exit = ExitCode(0)
            } else {
                observabilityService.trackError("YtDlpWrapper", "Python metadata rewrite failed", metadata = mapOf("error" to result.optString("error", "Unknown"), "filePath" to filePath))
            }
            exit
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Fatal error during metadata rewrite", e, mapOf("filePath" to filePath))
            ExitCode(1)
        }
    }

    fun fetchMetadataFromSource(artist: String, title: String): JSONObject? {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")
            val result = module.callAttr("search_metadata", artist, title)
            
            if (result == null || result.toString() == "None") return null
            JSONObject(result.toString())
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Error searching metadata", e, mapOf("artist" to artist, "title" to title))
            null
        }
    }

    fun checkUpdate(appFilesDir: String, forceRemote: Boolean = false): String {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")
            module.callAttr("check_yt_dlp_update", appFilesDir, forceRemote).toString()
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Error checking yt-dlp update", e)
            "Error: ${e.message}"
        }
    }

    fun performUpdate(appFilesDir: String): String {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("ytdown")
            module.callAttr("update_yt_dlp_if_needed", appFilesDir, true).toString()
        } catch (e: Exception) {
            observabilityService.trackError("YtDlpWrapper", "Error performing yt-dlp update", e)
            "Error: ${e.message}"
        }
    }
}
