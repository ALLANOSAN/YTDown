package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytdown.services.FileSystemScannerService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ytdown.utils.LocalLogger

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scannerService: FileSystemScannerService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            scannerService.fullSync()
            Result.success()
        } catch (e: Exception) {
            LocalLogger.error("Erro na sincronização automática: ${e.message}", tag = "SyncWorker")
            Result.retry()
        }
    }
}
