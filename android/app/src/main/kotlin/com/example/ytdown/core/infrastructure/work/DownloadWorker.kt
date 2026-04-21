package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.YtDlpWrapper
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: DownloadRepository,
    private val engine: DownloadEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("VIDEO_ID") ?: return Result.failure()
        val url = inputData.getString("VIDEO_URL") ?: return Result.failure()
        val path = inputData.getString("OUTPUT_PATH") ?: return Result.failure()

        return executeDownload(id, url, File(path))
    }

    // Regra 1: Um nível de indentação
    // Regra 2: Sem ELSE
    private suspend fun executeDownload(id: String, url: String, output: File): Result {
        val exitCode = ytDlp.downloadVideo(url, output)
        
        updateStatus(id, exitCode.isSuccess())
        
        if (exitCode.isSuccess()) return Result.success()
        
        return Result.failure()
    }

    private suspend fun updateStatus(id: String, success: Boolean) {
        val item = repository.find(id) ?: return
        
        val status = if (success) "completed" else "failed"
        val progress = if (success) 1.0 else 0.0
        
        repository.persist(item.copy(
            status = status,
            progress = progress
        ))
    }
}