package com.example.ytdown

import android.content.Context
import android.util.Log
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.MediaScanner
import com.example.ytdown.core.infrastructure.MimeTypeResolver

class DownloadMetadataManager(
    private val scanner: MediaScanner,
    private val resolver: MimeTypeResolver
) {
    private const val TAG = "DownloadService"

    fun fetchVideoInfo(context: Context, url: VideoUrl): VideoInfoJson {
        val result = try {
            PythonBridge.invokePythonJson(
                context,
                "fetch_video_info",
                url.value,
                PythonBridge.appFilesDirPath(context),
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao buscar info: ${e.message}", e)
            """{"success": false, "error": "${e.message}"}"""
        }
        return VideoInfoJson(result)
    }

    // Regra 1: Lógica linear, sem aninhamento excessivo
    fun rewriteMetadata(
        context: Context,
        path: FilePath,
        metadata: MediaMetadata
    ) {
        val mime = resolver.fromPath(path)
        scanner.scanSync(path, mime)
        
        // Chamar ponte Python para os metadados reais
        PythonBridge.invokePythonJson(
            context, 
            "rewrite_file_metadata", 
            path.value, 
            metadata.title.value,
            metadata.artist.value,
            metadata.album.value
        )
    }
}
