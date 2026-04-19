package com.example.ytdown

import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DownloadService {
    private const val TAG = "DownloadService"

    fun fetchVideoInfo(context: Context, url: String, result: MethodChannel.Result) {
        try {
            val jsonResult = PythonBridge.invokePythonJson(
                context,
                "fetch_video_info",
                url,
                PythonBridge.appFilesDirPath(context),
            )
            respondSuccess(result, jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao buscar info: ${e.message}", e)
            respondStructuredFailure(result, "fetch_video_info", "FETCH_ERROR", e)
        }
    }

    fun downloadVideo(
        context: Context,
        url: String,
        outputPath: String,
        type: String,
        format: String,
        quality: String,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        result: MethodChannel.Result,
    ) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            Log.d(TAG, "🔵 Iniciando download: $url -> $outputPath")
            Log.d(TAG, "🔵 NativeLibDir (ffmpeg): $nativeLibDir")

            val jsonResult = PythonBridge.invokePythonJson(
                context,
                "download_video",
                url,
                outputPath,
                type,
                quality,
                nativeLibDir,
                PythonBridge.appFilesDirPath(context),
                artist,
                album,
                artworkUrl,
                format,
            )

            Log.d(TAG, "✅ Download result: $jsonResult")
            respondSuccess(result, jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no download: ${e.message}", e)
            respondStructuredFailure(result, "download_video", "DOWNLOAD_ERROR", e)
        }
    }

    fun rewriteMetadata(
        context: Context,
        filePath: String,
        title: String,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        result: MethodChannel.Result,
    ) {
        try {
            val jsonResult = PythonBridge.invokePythonJson(
                context,
                "rewrite_file_metadata",
                filePath,
                title,
                artist,
                album,
                artworkUrl,
            )

            val mimeType = resolveMediaMimeType(filePath)
            Log.d(TAG, "🔄 [MediaScanner] Iniciando scan para: $filePath")
            Log.d(TAG, "🔄 [MediaScanner] MIME type: $mimeType")
            val scanStartTime = System.currentTimeMillis()

            val latch = CountDownLatch(1)
            var scanUri: String? = null
            var scanSuccess = false

            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                arrayOf(mimeType),
            ) { _, uri ->
                scanUri = uri?.toString()
                scanSuccess = uri != null
                val scanDuration = System.currentTimeMillis() - scanStartTime
                Log.d(TAG, "✅ [MediaScanner] Concluído em ${scanDuration}ms: $filePath -> $uri")
                latch.countDown()
            }

            val completed = latch.await(10, TimeUnit.SECONDS)
            val scanDuration = System.currentTimeMillis() - scanStartTime

            if (!completed) {
                Log.w(TAG, "⚠️ [MediaScanner] TIMEOUT após 10s para: $filePath")
            }
            if (completed && !scanSuccess) {
                Log.w(TAG, "⚠️ [MediaScanner] Falhou (URI nulo) para: $filePath")
            }
            if (completed && scanSuccess) {
                Log.d(TAG, "✅ [MediaScanner] Sucesso em ${scanDuration}ms")
            }

            if (scanUri != null) {
                try {
                    val uri = Uri.parse(scanUri)
                    val values = ContentValues().apply {
                        if (title.isNotBlank()) {
                            put(MediaStore.Audio.Media.TITLE, title)
                        }
                        if (!artist.isNullOrBlank()) {
                            put(MediaStore.Audio.Media.ARTIST, artist)
                            put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
                        }
                        if (!album.isNullOrBlank()) {
                            put(MediaStore.Audio.Media.ALBUM, album)
                        }
                    }

                    if (values.size() > 0) {
                        val updatedRows = context.contentResolver.update(uri, values, null, null)
                        Log.d(TAG, "✅ [ContentResolver] MediaStore atualizado: $updatedRows linhas")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ [ContentResolver] Falha ao atualizar MediaStore: ${e.message}")
                }
            }

            respondSuccess(result, jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao regravar metadados: ${e.message}", e)
            respondStructuredFailure(result, "rewrite_file_metadata", "REWRITE_METADATA_ERROR", e)
        }
    }

    fun checkYtDlpUpdate(
        context: Context,
        forceRemote: Boolean,
        result: MethodChannel.Result,
    ) {
        try {
            val jsonResult = PythonBridge.invokePythonJson(
                context,
                "check_yt_dlp_update",
                PythonBridge.appFilesDirPath(context),
                forceRemote,
            )
            respondSuccess(result, jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao checar atualização yt-dlp: ${e.message}", e)
            respondStructuredFailure(result, "check_yt_dlp_update", "YTDLP_CHECK_ERROR", e)
        }
    }

    fun updateYtDlpIfNeeded(
        context: Context,
        force: Boolean,
        result: MethodChannel.Result,
    ) {
        try {
            val jsonResult = PythonBridge.invokePythonJson(
                context,
                "update_yt_dlp_if_needed",
                PythonBridge.appFilesDirPath(context),
                force,
            )
            respondSuccess(result, jsonResult)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao atualizar yt-dlp: ${e.message}", e)
            respondStructuredFailure(result, "update_yt_dlp_if_needed", "YTDLP_UPDATE_ERROR", e)
        }
    }

    fun checkFfmpeg(context: Context, result: MethodChannel.Result) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val ffmpegPath = "$nativeLibDir/libffmpeg_exe.so"
            val ffmpegFile = File(ffmpegPath)

            val info = mapOf(
                "path" to ffmpegPath,
                "exists" to ffmpegFile.exists(),
                "size" to ffmpegFile.length(),
                "canExecute" to ffmpegFile.canExecute(),
                "nativeLibDir" to nativeLibDir,
            )

            Log.d(TAG, "🔵 FFmpeg check: $info")
            respondSuccess(result, info.toString())
        } catch (e: Exception) {
            respondStructuredFailure(result, "check_ffmpeg", "CHECK_ERROR", e)
        }
    }

    fun batchRescanFiles(
        context: Context,
        paths: List<String>,
        result: MethodChannel.Result,
    ) {
        if (paths.isEmpty()) {
            respondSuccess(result, mapOf(
                "success" to true,
                "scanned" to 0,
                "failed" to 0,
                "total" to 0,
                "timeout" to false,
            ))
            return
        }

        Log.d(TAG, "🔄 [BatchRescan] Iniciando scan para ${paths.size} arquivos")
        val scanStartTime = System.currentTimeMillis()

        val mimeTypes = paths.map(::resolveMediaMimeType).toTypedArray()
        val latch = CountDownLatch(paths.size)
        var successCount = 0
        var failCount = 0
        val syncLock = Any()

        MediaScannerConnection.scanFile(
            context,
            paths.toTypedArray(),
            mimeTypes,
        ) { path, uri ->
            synchronized(syncLock) {
                if (uri != null) {
                    successCount++
                    Log.d(TAG, "✅ [BatchRescan] Sucesso: $path -> $uri")
                }
                if (uri == null) {
                    failCount++
                    Log.w(TAG, "⚠️ [BatchRescan] Falhou (URI nulo): $path")
                }
                latch.countDown()
            }
        }

        val completed = latch.await(60, TimeUnit.SECONDS)
        val scanDuration = System.currentTimeMillis() - scanStartTime
        val finalSuccessCount: Int
        val finalFailCount: Int

        synchronized(syncLock) {
            finalSuccessCount = successCount
            finalFailCount = failCount
        }

        Log.d(TAG, "📊 [BatchRescan] Concluído em ${scanDuration}ms:")
        Log.d(TAG, "   ✅ Sucesso: $finalSuccessCount")
        Log.d(TAG, "   ❌ Falhou: $finalFailCount")
        Log.d(TAG, "   📁 Total: ${paths.size}")
        if (!completed) {
            Log.w(TAG, "   ⏱️ TIMEOUT após 60s!")
        }

        respondSuccess(result, mapOf(
            "success" to completed,
            "scanned" to finalSuccessCount,
            "failed" to finalFailCount,
            "total" to paths.size,
            "timeout" to !completed,
            "durationMs" to scanDuration,
        ))
    }

    private fun resolveMediaMimeType(path: String): String? {
        if (path.endsWith(".mp3", ignoreCase = true)) {
            return "audio/mpeg"
        }
        if (path.endsWith(".m4a", ignoreCase = true)) {
            return "audio/mp4"
        }
        if (path.endsWith(".mp4", ignoreCase = true)) {
            return "video/mp4"
        }
        if (path.endsWith(".ogg", ignoreCase = true)) {
            return "audio/ogg"
        }
        if (path.endsWith(".opus", ignoreCase = true)) {
            return "audio/opus"
        }
        if (path.endsWith(".flac", ignoreCase = true)) {
            return "audio/flac"
        }
        return null
    }
}
