package com.example.ytdown.core.business

import com.chaquo.python.Python
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.PythonEnvironment
import java.io.File
import org.json.JSONObject

class YtDlpWrapper(
    private val env: PythonEnvironment
) {
    /**
     * Executa o download de vídeo/áudio usando yt-dlp via Chaquopy.
     */
    fun downloadVideo(
        url: VideoUrl,
        outputDir: File,
        options: DownloadOptions,
        metadata: MediaMetadata? = null,
        artworkUrl: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): ExitCode {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")
        
        val outputPath = File(outputDir, "%(title)s.%(ext)s").absolutePath
        
        // Criamos o callback que o Python vai chamar
        val progressCallback = object : Runnable {
            private var lastProgress = -1
            fun update(p: Int) {
                if (p != lastProgress) {
                    lastProgress = p
                    onProgress?.invoke(p)
                }
            }
            override fun run() {}
        }

        val resultJson = module.callAttr(
            "download_video",
            url.value,
            outputPath,
            options.type.value,
            options.quality,
            env.getNativeLibDir(),
            env.getAppFilesDir(),
            metadata?.artist?.value,
            metadata?.album?.value,
            artworkUrl,
            options.format,
            progressCallback
        ).toString()

        val result = JSONObject(resultJson)
        var exit = ExitCode(1)
        if (result.getBoolean("success")) {
            exit = ExitCode(0)
        }
        return exit
    }

    fun fetchVideoInfo(url: String): JSONObject {
        val py = Python.getInstance()
        val module = py.getModule("ytdown")
        val resultJson = module.callAttr("fetch_video_info", url, env.getAppFilesDir()).toString()
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
        
        val resultJson = module.callAttr(
            "rewrite_file_metadata",
            filePath,
            title,
            artist,
            album,
            artworkUrl
        ).toString()

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
