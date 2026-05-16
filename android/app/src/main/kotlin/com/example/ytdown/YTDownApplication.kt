package com.example.ytdown

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.core.infrastructure.work.SyncWorker
import com.example.ytdown.services.ObservabilityService
import com.example.ytdown.services.CacheGuardianWorker
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class YTDownApplication : Application(), Configuration.Provider {

    @Inject lateinit var playerManager: MusicPlayerManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var observabilityService: ObservabilityService

    private lateinit var coroutineExceptionHandler: CoroutineExceptionHandler

    override fun onCreate() {
        super.onCreate()

        // Inicializar Motor de Áudio BASS
        com.example.ytdown.core.audio.BassCore.initialize(this)

        // Sincronização automática
        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("FileSystemSync", ExistingPeriodicWorkPolicy.KEEP, syncWorkRequest)

        // Cache Guardian
        val cacheWorkRequest = PeriodicWorkRequestBuilder<CacheGuardianWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("CacheGuardian", ExistingPeriodicWorkPolicy.KEEP, cacheWorkRequest)

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil_image_cache")).maxSizeBytes(100L * 1024 * 1024).build() }
                .crossfade(true)
                .build()
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { playerManager.saveCurrentPositionNow() }
            override fun onDestroy(owner: LifecycleOwner) { playerManager.pause() }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
