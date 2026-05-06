package com.example.ytdown.core.infrastructure.work

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.ytdown.core.business.DownloadEngine
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: DownloadRepository,
        private val engine: DownloadEngine
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)
    private val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YTDown:DownloadLock")
    @Suppress("DEPRECATION")
    private val wifiLock = run {
        var wifiMode = WifiManager.WIFI_MODE_FULL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiManager.createWifiLock(wifiMode, "YTDown:WiFiLock")
    }

    private val progressScope = CoroutineScope(Dispatchers.IO + Job())

    private var lastProgressTime = AtomicLong(System.currentTimeMillis())
    private var lastProgressValue = -1
    private var lastDbUpdateTime = 0L

    override suspend fun doWork(): Result {
        android.util.Log.e("DownloadWorker", "🚀 doWork started!")
        return try {
            val id = inputData.getString("VIDEO_ID") ?: return Result.failure()
            val url = inputData.getString("VIDEO_URL") ?: return Result.failure()
            val path = inputData.getString("OUTPUT_PATH") ?: return Result.failure()
            val title = inputData.getString("TITLE") ?: "Download"
            android.util.Log.e("DownloadWorker", "📦 Starting download for: $title, path: $path")

            val metadata =
                    MediaMetadata(
                            MediaTitle(title),
                            ArtistName(inputData.getString("ARTIST") ?: ""),
                            AlbumName(inputData.getString("ALBUM") ?: "")
                    )

            val artworkUrl = inputData.getString("ARTWORK_URL")
            val downloadOptions =
                    DownloadOptions(
                            type =
                                    DownloadType.values().firstOrNull {
                                        it.value == inputData.getString("DOWNLOAD_TYPE")
                                    }
                                            ?: DownloadType.AUDIO,
                            format = inputData.getString("FORMAT") ?: "mp3",
                            quality = inputData.getString("QUALITY") ?: "192"
                    )

            wakeLock.acquire(30 * 60 * 1000L)
            wifiLock.acquire()
            setForeground(createForegroundInfo(title, 0))

            val startItem = repository.find(id)
            if (startItem != null) {
                repository.persist(startItem.copy(status = "downloading", progress = 0.0))
            }

            val watchdogScope = CoroutineScope(Dispatchers.IO + Job())
            val watchdogJob =
                    watchdogScope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(10_000)
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime.get() > 5 * 60 * 1000L) {
                                android.util.Log.e(
                                        "DownloadWorker",
                                        "⚠️ Watchdog: sem progresso por 5min. Abortando."
                                )
                                break
                            }
                        }
                    }

            val downloadResult =
                    engine.downloadAndTag(
                            VideoUrl(url),
                            File(path),
                            metadata,
                            downloadOptions,
                            artworkUrl
                    ) { progress ->
                        if (progress != lastProgressValue) {
                            lastProgressValue = progress
                            lastProgressTime.set(System.currentTimeMillis())
                            progressScope.launch { updateProgress(id, title, progress) }
                        }
                    }

            val success = downloadResult.exitCode.isSuccess()
            updateFinalStatus(
                    id,
                    success,
                    downloadResult.outputPath?.takeIf { it.isNotBlank() } ?: path
            )
            watchdogJob.cancel()
            progressScope.cancel()
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock.isHeld) wifiLock.release()

            if (success) Result.success() else Result.failure()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "❌ doWork CRASHED: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                    notificationId,
                    notificationHelper.buildProgressNotification(title, progress),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                    notificationId,
                    notificationHelper.buildProgressNotification(title, progress)
            )
        }
    }

    private suspend fun updateProgress(id: String, title: String, progress: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDbUpdateTime > 1000 || progress >= 100) {
            val item = repository.find(id) ?: return
            repository.persist(item.copy(progress = progress.toDouble() / 100.0))
            lastDbUpdateTime = currentTime
        }
        setForeground(createForegroundInfo(title, progress))
    }

    private suspend fun updateFinalStatus(
            id: String,
            success: Boolean,
            outputPath: String? = null
    ) {
        val item = repository.find(id) ?: return
        repository.persist(
                item.copy(
                        status = if (success) "completed" else "failed",
                        progress = if (success) 1.0 else 0.0,
                        outputPath = outputPath?.takeIf { it.isNotBlank() } ?: item.outputPath
                )
        )
    }
}
