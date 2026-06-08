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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

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

    /** FIX #4: Watchdog now sets a flag that the download loop can check */
    private val stalled = AtomicBoolean(false)

    override suspend fun doWork(): Result {
        android.util.Log.e("DownloadWorker", "🚀 doWork started!")
        wakeLock.acquire(30 * 60 * 1000L)
        wifiLock.acquire()

        val result = try {
            val id = inputData.getString("VIDEO_ID") ?: return Result.failure()
            val url = inputData.getString("VIDEO_URL") ?: return Result.failure()
            val finalDestPath = inputData.getString("OUTPUT_PATH") ?: return Result.failure()
            val title = inputData.getString("TITLE") ?: "Download"
            
            android.util.Log.d("DOWNLOAD_FLOW", "🚀 Iniciando Worker para: $title")
            android.util.Log.d("STORAGE_DEBUG", "🎯 Destino final desejado: $finalDestPath")

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

            setForeground(createForegroundInfo(title, 0))

            val startItem = repository.find(id)
            if (startItem != null) {
                repository.persist(startItem.copy(status = "downloading", progress = 0.0))
            }

            // ✅ ESTRATÉGIA DE STORAGE DEFINITIVA:
            // Baixamos sempre no diretório de cache privado do app para evitar Permission Denied do Python.
            // O Python (yt-dlp) precisa de acesso direto ao sistema de arquivos via C, o que é bloqueado em pastas externas no Android 11+.
            val tempDownloadDir = File(applicationContext.cacheDir, "downloads").apply { if (!exists()) mkdirs() }
            
            android.util.Log.d("STORAGE_DEBUG", "🛠️ Usando cache privado para download: ${tempDownloadDir.absolutePath}")

            val downloadResult =
                    withTimeoutOrNull(30 * 60 * 1000L) {
                        engine.downloadAndTag(
                                VideoUrl(url),
                                tempDownloadDir,
                                metadata,
                                downloadOptions,
                                artworkUrl
                        ) { progress ->
                            if (!progressScope.isActive) return@downloadAndTag
                            if (progress != lastProgressValue) {
                                lastProgressValue = progress
                                lastProgressTime.set(System.currentTimeMillis())
                                progressScope.launch { updateProgress(id, title, progress) }
                            }
                        }
                    }

            if (downloadResult == null) {
                android.util.Log.e("DOWNLOAD_FLOW", "⚠️ Timeout de 30 min atingido!")
                updateFinalStatus(id, success = false)
                return Result.failure()
            }

            val success = downloadResult.exitCode.isSuccess()
            val tempFilePath = downloadResult.outputPath

            if (success && tempFilePath != null) {
                android.util.Log.d("DOWNLOAD_FLOW", "📦 Download no cache concluído. Iniciando exportação...")
                
                // Agora exportamos do cache privado para a galeria pública (/Music ou /Video)
                val storageService = com.example.ytdown.services.StorageService.getInstance()
                
                val mediaType = if (downloadOptions.type == DownloadType.AUDIO) StorageMediaType("audio") else StorageMediaType("video")
                
                try {
                    val exportedUri = storageService.exportToPublicCollection(
                        context = applicationContext,
                        sourcePath = StoragePath(tempFilePath),
                        displayName = File(tempFilePath).name,
                        mediaType = mediaType,
                        mimeType = StorageMimeType(if (mediaType.isAudio()) "audio/*" else "video/*"),
                        allowUserInteractionFallback = true
                    )

                    android.util.Log.d("DOWNLOAD_FLOW", "✨ Exportação para MediaStore concluída: $exportedUri")
                    
                    // Inicia processamento de metadados e capas (MusicBrainz/FanArt/Mutagen)
                    try {
                        val importProcessor = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            applicationContext, 
                            com.example.ytdown.core.infrastructure.di.ImportProcessorEntryPoint::class.java
                        ).mediaImportProcessor()
                        importProcessor.process(
                            tempFilePath,
                            originalTitle = title,
                            knownArtist = metadata.artist.value.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Desconhecido", ignoreCase = true) },
                            knownAlbum = metadata.album.value.takeIf { it.isNotBlank() && !it.equals("Unknown Album", ignoreCase = true) },
                            forceEnrichment = true
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("STORAGE_DEBUG", "⚠️ Erro no processamento de metadados: ${e.message}")
                    }

                    // Limpar arquivo temporário
                    File(tempFilePath).delete()
                    
                    updateFinalStatus(id, success = true, outputPath = finalDestPath, exportedPath = exportedUri?.toString())
                    Result.success()
                } catch (e: Exception) {
                    android.util.Log.e("STORAGE_DEBUG", "❌ Falha ao exportar para MediaStore: ${e.message}")
                    updateFinalStatus(id, success = false)
                    Result.failure()
                }
            } else {
                android.util.Log.e("DOWNLOAD_FLOW", "❌ Download falhou no engine.")
                updateFinalStatus(id, success = false)
                Result.failure()
            }
        } catch (e: CancellationException) {
            android.util.Log.e("DOWNLOAD_FLOW", "🛑 Worker CANCELADO")
            Result.failure()
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD_FLOW", "❌ Erro inesperado no Worker: ${e.message}", e)
            Result.failure()
        } finally {
            progressScope.cancel()
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock.isHeld) wifiLock.release()
        }


        return result
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
            outputPath: String? = null,
            exportedPath: String? = null
    ) {
        val item = repository.find(id) ?: return
        repository.persist(
                item.copy(
                        status = if (success) "completed" else "failed",
                        progress = if (success) 1.0 else 0.0,
                        outputPath = outputPath?.takeIf { it.isNotBlank() } ?: item.outputPath,
                        exportedPath = exportedPath ?: item.exportedPath
                )
        )
    }
}
