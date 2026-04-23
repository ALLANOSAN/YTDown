package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadEngine
import com.example.ytdown.core.infrastructure.NotificationHelper
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: DownloadRepository,
    private val engine: DownloadEngine
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)
    private val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    override suspend fun doWork(): Result {
        val id = inputData.getString("VIDEO_ID") ?: return Result.failure()
        val url = inputData.getString("VIDEO_URL") ?: return Result.failure()
        val path = inputData.getString("OUTPUT_PATH") ?: return Result.failure()
        val title = inputData.getString("TITLE") ?: "Download"

        val metadata = MediaMetadata(
            MediaTitle(title),
            ArtistName(inputData.getString("ARTIST") ?: ""),
            AlbumName(inputData.getString("ALBUM") ?: "")
        )

        val downloadOptions = DownloadOptions(
            type = DownloadType.values().firstOrNull { it.value == inputData.getString("DOWNLOAD_TYPE") } ?: DownloadType.AUDIO,
            format = inputData.getString("FORMAT") ?: "mp3",
            quality = inputData.getString("QUALITY") ?: "192"
        )

        // Iniciar notificação em primeiro plano
        setForeground(createForegroundInfo(title, 0))

        return executeDownload(id, VideoUrl(url), File(path), metadata, downloadOptions)
    }

    private suspend fun executeDownload(
        id: String,
        url: VideoUrl,
        output: File,
        metadata: MediaMetadata,
        options: DownloadOptions
    ): Result {
        val exitCode = engine.downloadAndTag(url, output, metadata, options) { progress ->
            // Atualizar banco de dados e notificação em tempo real
            kotlinx.coroutines.runBlocking {
                updateProgress(id, metadata.title.value, progress)
            }
        }
        
        updateFinalStatus(id, exitCode.isSuccess())
        
        if (exitCode.isSuccess()) return Result.success()
        return Result.failure()
    }

    private suspend fun updateProgress(id: String, title: String, progress: Int) {
        val item = repository.find(id) ?: return
        repository.persist(item.copy(progress = progress.toDouble() / 100.0))
        setForeground(createForegroundInfo(title, progress))
    }

    private suspend fun updateFinalStatus(id: String, success: Boolean) {
        val item = repository.find(id) ?: return
        val status = if (success) "completed" else "failed"
        repository.persist(item.copy(status = status, progress = if (success) 1.0 else 0.0))
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        return ForegroundInfo(notificationId, notificationHelper.buildProgressNotification(title, progress))
    }
}