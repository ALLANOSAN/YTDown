package com.example.ytdown.core.business

import androidx.paging.PagingData
import androidx.work.*
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

    fun streamPaged(query: String = "", typeFilter: Int? = null): Flow<PagingData<DownloadItemEntity>> =
        repository.streamPaged(query, typeFilter)

    suspend fun schedule(
        url: VideoUrl, path: FilePath, meta: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null
    ) {
        android.util.Log.e("DownloadScheduler", "🔍 Scheduling: Artist=${meta.artist.value}, Album=${meta.album.value}, Title=${meta.title.value}")
        val id = UUID.randomUUID().toString()

        // ✅ NOTA: Não tentamos criar pastas externas diretamente via File API (mkdirs)
        // porque isso falha no Android 11+ (Scoped Storage).
        // O download agora ocorre no cache privado e é exportado via MediaStore/SAF.
        // val pathDir = File(path.value)
        // if (!pathDir.exists()) pathDir.mkdirs()


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
            // Passa o DIRETÓRIO destino. O YtDlpWrapper monta o nome do arquivo
            // via %(title)s.%(ext)s internamente — o yt-dlp usa o título real do vídeo.
            putString("OUTPUT_PATH", path.value)
            putString("TITLE", meta.title.value)
            putString("ARTIST", meta.artist.value)
            putString("ALBUM", meta.album.value)
            putString("DOWNLOAD_TYPE", options.type.value)
            putString("FORMAT", options.format)
            putString("QUALITY", options.quality)
            putString("ARTWORK_URL", artworkUrl)
        }.build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "download_$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
