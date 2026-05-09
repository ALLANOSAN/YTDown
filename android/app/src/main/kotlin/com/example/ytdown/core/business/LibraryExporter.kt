package com.example.ytdown.core.business

import android.content.Context
import com.example.ytdown.core.domain.StorageMediaType
import com.example.ytdown.core.domain.StorageMimeType
import com.example.ytdown.core.domain.StoragePath
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.StorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryExporter @Inject constructor(
    private val databaseService: DatabaseService,
    @param:ApplicationContext private val context: Context
) {
    suspend fun exportAll(
        onProgress: (Float, String) -> Unit
    ): Pair<Int, Int> {
        val items = databaseService.getAllDownloads().filter {
            it.status == "completed" && it.outputPath.isNotBlank()
        }

        if (items.isEmpty()) return 0 to 0

        var exported = 0
        var failed = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Exportando: ${item.title}")

            val sourceFile = File(item.outputPath)
            if (!sourceFile.exists()) {
                failed++
                continue
            }

            val isAudio = item.type == 0
            val ext = item.outputPath.substringAfterLast('.', "").lowercase()

            val mimeType = StorageMimeType(
                when (ext) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "flac" -> "audio/flac"
                    "opus" -> "audio/opus"
                    "ogg" -> "audio/ogg"
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    else -> "application/octet-stream"
                }
            )

            val mediaType = if (isAudio) StorageMediaType("audio") else StorageMediaType("video")

            try {
                StorageService.getInstance().exportToPublicCollection(
                    context = context,
                    sourcePath = StoragePath(item.outputPath),
                    displayName = item.title.ifBlank { sourceFile.name },
                    mediaType = mediaType,
                    mimeType = mimeType,
                    allowUserInteractionFallback = false
                )
                exported++
            } catch (e: Exception) {
                failed++
            }
        }
        return exported to failed
    }
}
