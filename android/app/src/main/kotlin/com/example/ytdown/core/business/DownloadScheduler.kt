package com.example.ytdown.core.business

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class DownloadScheduler @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager
) {
    // Regra 8: Apenas duas variáveis de instância.
    // streamAll() é um método do repository, não uma variável de instância aqui.
    fun stream(): Flow<List<DownloadItemEntity>> = repository.streamAll()

    suspend fun schedule(
        url: VideoUrl, path: FilePath, meta: MediaMetadata,
        options: DownloadOptions
    ) {
        val id = UUID.randomUUID().toString()

        // Nome do arquivo: Artista - Album - Titulo.ext
        val fileName = "${meta.artist.value} - ${meta.album.value} - ${meta.title.value}"
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .trim()
        
        val finalPath = "${path.value}/$fileName.${options.format}"

        val item = DownloadItemEntity(
            id = id, url = url.value, title = meta.title.value,
            filePath = finalPath, status = "pending", progress = 0.0,
            artist = meta.artist.value, album = meta.album.value
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
        }.build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        // Usar ExistingWorkPolicy.APPEND_OR_REPLACE para downloads de playlist
        workManager.enqueueUniqueWork(
            "download-${id}", // Nome único para cada download
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}