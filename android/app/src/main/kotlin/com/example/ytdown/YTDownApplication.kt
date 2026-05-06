package com.example.ytdown

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.services.ObservabilityService
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytdown.services.CacheGuardianWorker
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import java.util.concurrent.TimeUnit

/**
 * Classe de aplicação principal.
 * Gerencia o ciclo de vida global e limpeza de recursos.
 */
@HiltAndroidApp
class YTDownApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var playerManager: MusicPlayerManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var observabilityService: ObservabilityService

    private lateinit var coroutineExceptionHandler: CoroutineExceptionHandler
    private val appCoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)
    }

    override fun onCreate() {
        super.onCreate()

        // Agendar CacheGuardian (roda uma vez por semana)
        val cacheWorkRequest = PeriodicWorkRequestBuilder<CacheGuardianWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheGuardian",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            cacheWorkRequest
        )

        // Coil — cache de disco (100MB) + memória (20% da RAM disponível)
        // Evita recarregar thumbnails e capas da rede a cada reinício do app
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_image_cache"))
                        .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                        .build()
                }
                .crossfade(true)
                .build()
        )

        coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            observabilityService.trackError(
                "CoroutineException",
                throwable.message ?: "Unhandled coroutine exception",
                throwable
            )
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            observabilityService.trackError(
                "UncaughtException",
                throwable.message ?: "Application crashed without message",
                throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // FIX 3 — Salva posição imediatamente quando app vai para background
                // Garante que mesmo se o processo for morto pelo SO, a posição é preservada
                playerManager.saveCurrentPositionNow()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                playerManager.pause()
            }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onTerminate() {
        playerManager.pause()
        super.onTerminate()
    }
}
