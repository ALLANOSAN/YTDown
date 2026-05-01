package com.example.ytdown.core.infrastructure.work

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadEngine
import com.example.ytdown.core.infrastructure.NotificationHelper
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Worker de Download com Watchdog de Sobrevivência.
 * Migrado do Flutter (lib/services/foreground_task_service.dart).
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: DownloadRepository,
    private val engine: DownloadEngine
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)
    private val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YTDown:DownloadLock")
    @Suppress("DEPRECATION")
    private val wifiLock = run {
        var wifiMode = WifiManager.WIFI_MODE_FULL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiManager.createWifiLock(wifiMode, "YTDown:WiFiLock")
    }

    private val progressScope = CoroutineScope(Dispatchers.IO + Job())

    // 🕵️ Lote 3.1: Zombie Watchdog State
    private var lastProgressTime = AtomicLong(System.currentTimeMillis())
    private var lastProgressValue = -1
    private var lastDbUpdateTime = 0L // Variável para throttling

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

        val artworkUrl = inputData.getString("ARTWORK_URL")
        val downloadOptions = DownloadOptions(
            type = DownloadType.values().firstOrNull { it.value == inputData.getString("DOWNLOAD_TYPE") } ?: DownloadType.AUDIO,
            format = inputData.getString("FORMAT") ?: "mp3",
            quality = inputData.getString("QUALITY") ?: "192"
        )

        wakeLock.acquire(30 * 60 * 1000L)
        wifiLock.acquire()
        setForeground(createForegroundInfo(title, 0))

        // Inicia o Watchdog em uma corrotina separada
        val watchdogScope = CoroutineScope(Dispatchers.IO + Job())
        val watchdogJob = watchdogScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                val now = System.currentTimeMillis()
                // 🚨 Se não houver progresso por 30 segundos, aborta (Evita drenar bateria)
                if (now - lastProgressTime.get() > 30000) {
                    android.util.Log.e("DownloadWorker", "⚠️ Watchdog: Processo Zumbi detectado. Abortando.")
                    break
                }
            }
        }

        return try {
            val exitCode = engine.downloadAndTag(VideoUrl(url), File(path), metadata, downloadOptions, artworkUrl) { progress ->
                // Atualiza o watchdog
                if (progress != lastProgressValue) {
                    lastProgressValue = progress
                    lastProgressTime.set(System.currentTimeMillis())
                }
                progressScope.launch {
                    try {
                        updateProgress(id, title, progress)
                    } catch (ignored: Exception) {
                        android.util.Log.w("DownloadWorker", "Falha ao atualizar progresso", ignored)
                    }
                }
            }
            
            updateFinalStatus(id, exitCode.isSuccess())
            var result = Result.failure()
            if (exitCode.isSuccess()) {
                result = Result.success()
            }
            result
        } finally {
            watchdogJob.cancel()
            progressScope.cancel()
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock.isHeld) wifiLock.release()
        }
    }

    private suspend fun updateProgress(id: String, title: String, progress: Int) {
        val currentTime = System.currentTimeMillis()
        // Throttling: apenas atualiza o banco se passou 1s ou é final
        if (currentTime - lastDbUpdateTime > 1000 || progress >= 100) {
            val item = repository.find(id) ?: return
            repository.persist(item.copy(progress = progress.toDouble() / 100.0))
            lastDbUpdateTime = currentTime
        }
        // UI sempre recebe a atualização para manter a notificação fluida
        setForeground(createForegroundInfo(title, progress))
    }

    private suspend fun updateFinalStatus(id: String, success: Boolean) {
        val item = repository.find(id) ?: return
        var status = "failed"
        if (success) {
            status = "completed"
        }
        var progressValue = 0.0
        if (success) {
            progressValue = 1.0
        }
        repository.persist(item.copy(status = status, progress = progressValue))
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        return ForegroundInfo(notificationId, notificationHelper.buildProgressNotification(title, progress))
    }
}
