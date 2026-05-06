package com.example.ytdown.core.business

import com.chaquo.python.Python
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.core.infrastructure.PythonEnvironment
import java.io.File
import org.json.JSONObject

data class DownloadResult(val exitCode: ExitCode, val outputPath: String? = null)

class YtDlpWrapper(
        private val env: PythonEnvironment,
        private val binaryOrchestrator: BinaryOrchestrator
) {
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

        return DownloadResult(
                exitCode = if (success) ExitCode(0) else ExitCode(1),
                outputPath = filename
        )
    }

    fun fetchVideoInfo(url: String, appFilesDir: String): JSONObject {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")
        val resultJson = module.callAttr("fetch_video_info", url, appFilesDir).toString()
        return JSONObject(resultJson)
    }

    fun rewriteMetadata(
            filePath: String,
            title: String,
            artist: String?,
            album: String?,
            artworkUrl: String?
    ): ExitCode {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")

        val resultJson =
                module.callAttr("rewrite_file_metadata", filePath, title, artist, album, artworkUrl)
                        .toString()

        val result = JSONObject(resultJson)
        var exit = ExitCode(1)
        if (result.getBoolean("success")) {
            exit = ExitCode(0)
        }
        return exit
    }

    fun checkUpdate(appFilesDir: String, forceRemote: Boolean = false): String {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")
        return module.callAttr("check_yt_dlp_update", appFilesDir, forceRemote).toString()
    }

    fun performUpdate(appFilesDir: String): String {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")
        return module.callAttr("update_yt_dlp_if_needed", appFilesDir, true).toString()
    }
}
