package com.example.ytdown.core.business

import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.core.infrastructure.NativeProcessExecutor
import com.example.ytdown.core.infrastructure.PythonEnvironment
import java.io.File
import java.util.regex.Pattern

class YtDlpWrapper(
    private val executor: NativeProcessExecutor,
    private val env: PythonEnvironment
) {
    private val progressPattern = Pattern.compile("\\[download\\]\\s+([\\d.]+)%")

    fun downloadVideo(
        url: VideoUrl, 
        outputDir: File, 
        options: DownloadOptions,
        onProgress: ((Int) -> Unit)? = null
    ): ExitCode {
        val pythonPath = env.getBinaryPath("python3.13").absolutePath
        val scriptPath = env.getBinaryPath("ytdown.py").absolutePath
        
        val command = listOf(
            pythonPath, 
            scriptPath, 
            "--url", url.value, 
            "--output", outputDir.absolutePath,
            "--format-type", options.type.value,
            "--quality", options.quality,
            "--selected-format", options.format,
            "--native-lib-dir", env.getBinaryPath("").parentFile?.absolutePath ?: ""
        )

        val result = executor.run(command, outputDir) { line ->
            parseProgress(line, onProgress)
        }
        
        return result.first
    }

    private fun parseProgress(line: String, callback: ((Int) -> Unit)?) {
        val matcher = progressPattern.matcher(line)
        if (matcher.find()) {
            val percent = matcher.group(1)?.toFloatOrNull()?.toInt() ?: 0
            callback?.invoke(percent)
        }
    }

    fun rewriteMetadata(filePath: String, title: String, artist: String?, album: String?, artworkUrl: String?): ExitCode {
        val pythonPath = env.getBinaryPath("python3.13").absolutePath
        val scriptPath = env.getBinaryPath("ytdown.py").absolutePath
        
        val command = mutableListOf(
            pythonPath,
            scriptPath,
            "--rewrite-metadata",
            "--file-path", filePath,
            "--title", title
        )
        
        artist?.let { command.addAll(listOf("--artist", it)) }
        album?.let { command.addAll(listOf("--album", it)) }
        artworkUrl?.let { command.addAll(listOf("--artwork-url", it)) }

        return executor.run(command, File(filePath).parentFile ?: File(".")).first
    }

    fun checkUpdate(appFilesDir: String): String {
        val pythonPath = env.getBinaryPath("python3.13").absolutePath
        val scriptPath = env.getBinaryPath("ytdown.py").absolutePath
        
        val command = listOf(
            pythonPath,
            scriptPath,
            "--check-update",
            "--app-files-dir", appFilesDir
        )
        
        return executor.run(command, File(appFilesDir)).second.stdout
    }

    fun performUpdate(appFilesDir: String): String {
        val pythonPath = env.getBinaryPath("python3.13").absolutePath
        val scriptPath = env.getBinaryPath("ytdown.py").absolutePath
        
        val command = listOf(
            pythonPath,
            scriptPath,
            "--perform-update",
            "--app-files-dir", appFilesDir
        )
        
        return executor.run(command, File(appFilesDir)).second.stdout
    }
}