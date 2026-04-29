package com.example.ytdown.core.business

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject

class DownloadScheduler @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager
) {
    fun stream(): Flow<List<DownloadItemEntity>> = repository.stream()

    suspend fun schedule(
        url: VideoUrl, path: FilePath, meta: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null
    ) {
        val id = UUID.randomUUID().toString()

        // Garantir que a pasta de destino exista antes de agendar o download.
        val pathDir = File(path.value)
        if (!pathDir.exists()) pathDir.mkdirs()

        // Nome do arquivo: Artista - Album - Titulo.ext
        val fileName = "${meta.artist.value} - ${meta.album.value} - ${meta.title.value}"
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .trim()
        
        val finalPath = "${path.value}/$fileName.${options.format}"

        var downloadType = 1
        if (options.type == DownloadType.AUDIO) {
            downloadType = 0
        }

        val item = DownloadItemEntity(
            id = id,
            url = url.value,
            title = meta.title.value,
            outputPath = "",
            status = "pending",
            progress = 0.0,
            artist = meta.artist.value,
            album = meta.album.value,
            type = downloadType,
            format = options.format,
            quality = options.quality
        )
        repository.persist(item)
        
        val data = Data.Builder().apply {
            putString("VIDEO_ID", id)
            putString("VIDEO_URL", url.value)
            putString("OUTPUT_PATH", finalPath)
            putString("TITLE", meta.title.value)
            putString("ARTIST", meta.artist.value)
            putString("ALBUM", meta.album.value)
            putString("DOWNLOAD_TYPE", options.type.value)
            putString("FORMAT", options.format)
            putString("QUALITY", options.quality)
            putString("ARTWORK_URL", artworkUrl)
        }.build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            "download-${id}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
