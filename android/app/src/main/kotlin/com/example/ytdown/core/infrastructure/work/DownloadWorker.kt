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
import com.example.ytdown.core.infrastructure.persistence.SongDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: DownloadRepository,
        private val engine: DownloadEngine,
        private val songDao: SongDao
) : CoroutineWorker(context, params) {

    private val notificationHelper = NotificationHelper(context)

    // ID FIXO: todos os itens da fila compartilham a MESMA notificação,
    // que é atualizada em vez de recriada a cada item.
    private val notificationId = NOTIF_ID

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
        android.util.Log.e("DownloadWorker", "🚀 Queue worker iniciado!")

        // Libera itens travados de um worker anterior abortado (sem graça).
        runCatching { repository.resetStuckDownloading() }

        wakeLock.acquire(LOCK_TIMEOUT)
        wifiLock.acquire()

        setForeground(createForegroundInfo("Preparando downloads…", 0))

        return try {
            var count = 0
            while (true) {
                val item = repository.nextPending() ?: break

                // Re-adquire os locks caso algum tenha expirado durante a fila longa.
                if (!wakeLock.isHeld) wakeLock.acquire(LOCK_TIMEOUT)
                if (!wifiLock.isHeld) wifiLock.acquire()

                processItem(item)
                count++
            }
            android.util.Log.d("DOWNLOAD_FLOW", "✅ Fila concluída. Itens processados: $count")
            Result.success()
        } catch (e: CancellationException) {
            android.util.Log.e("DOWNLOAD_FLOW", "🛑 Worker CANCELADO — encerrando fila (itens pendentes podem ser reagendados)")
            Result.failure()
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD_FLOW", "❌ Erro inesperado no Worker: ${e.message}", e)
            Result.failure()
        } finally {
            progressScope.cancel()
            if (wakeLock.isHeld) wakeLock.release()
            if (wifiLock.isHeld) wifiLock.release()
        }
    }

    /**
     * Baixa UM item da fila (lido diretamente da entidade persistida) e atualiza
     * seu status. Continua para o próximo item no loop de [doWork], independente
     * de falha pontual (a menos que o worker seja cancelado).
     */
    private suspend fun processItem(item: DownloadItemEntity) {
        val id = item.id
        val url = item.url
        val finalDestPath = item.outputPath
        val title = item.title.ifBlank { "Download" }

        android.util.Log.d("DOWNLOAD_FLOW", "🚀 Processando item: $title")

        val metadata = MediaMetadata(
                MediaTitle(title),
                ArtistName(item.artist ?: ""),
                AlbumName(item.album ?: "")
        )

        val downloadOptions = DownloadOptions(
                type = if (item.type == 0) DownloadType.AUDIO else DownloadType.VIDEO,
                format = item.format.ifBlank { "mp3" },
                quality = item.quality.ifBlank { "192" }
        )

        val artworkUrl = item.artworkUrl

        setForeground(createForegroundInfo(title, 0))

        val startItem = repository.find(id)
        if (startItem != null) {
            repository.persist(startItem.copy(status = "downloading", progress = 0.0))
        }

        // ✅ ESTRATÉGIA DE STORAGE DEFINITIVA:
        // Baixamos sempre no diretório de cache privado do app para evitar Permission Denied do Python.
        val tempDownloadDir = File(applicationContext.cacheDir, "downloads").apply { if (!exists()) mkdirs() }

        android.util.Log.d("STORAGE_DEBUG", "🛠️ Usando cache privado para download: ${tempDownloadDir.absolutePath}")

        try {
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
                android.util.Log.e("DOWNLOAD_FLOW", "⚠️ Timeout de 30 min atingido para $title!")
                updateFinalStatus(id, success = false)
                return
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

                        // Sincronizar artwork paths do SongEntity para o DownloadItemEntity (tabela downloads)
                        try {
                            val song = songDao.getByPath(tempFilePath)
                            if (song != null) {
                                val download = repository.find(id)
                                if (download != null) {
                                    repository.persist(
                                        download.copy(
                                            albumArtPath = song.albumArtwork ?: download.albumArtPath,
                                            artistArtPath = song.artistArtwork ?: download.artistArtPath,
                                            title = song.title,
                                            artist = song.artist,
                                            album = song.album
                                        )
                                    )
                                }
                                android.util.Log.d("DOWNLOAD_FLOW", "Sync artwork: ${song.artist} - ${song.title} | albumArt=${song.albumArtwork != null} artistArt=${song.artistArtwork != null}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DOWNLOAD_FLOW", "Erro no sync de artwork: ${e.message}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("STORAGE_DEBUG", "⚠️ Erro no processamento de metadados: ${e.message}")
                    }

                    // Limpar arquivo temporário
                    File(tempFilePath).delete()

                    updateFinalStatus(id, success = true, outputPath = finalDestPath, exportedPath = exportedUri?.toString())
                } catch (e: Exception) {
                    android.util.Log.e("STORAGE_DEBUG", "❌ Falha ao exportar para MediaStore: ${e.message}")
                    updateFinalStatus(id, success = false)
                }
            } else {
                android.util.Log.e("DOWNLOAD_FLOW", "❌ Download falhou no engine para $title.")
                updateFinalStatus(id, success = false)
            }
        } catch (e: CancellationException) {
            // Item cancelado: interrompe o worker inteiro (o resto da fila fica "pending").
            android.util.Log.e("DOWNLOAD_FLOW", "🛑 Item cancelado: $title")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DOWNLOAD_FLOW", "❌ Erro no item $title: ${e.message}", e)
            updateFinalStatus(id, success = false)
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

    companion object {
        /** ID fixo da notificação de progresso (compartilhado por toda a fila). */
        private const val NOTIF_ID = 1001
        /** Timeout do wakelock por item da fila (re-adquirido a cada item). */
        private val LOCK_TIMEOUT = 30L * 60 * 1000
    }
}
