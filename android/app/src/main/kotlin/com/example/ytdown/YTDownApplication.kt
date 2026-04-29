package com.example.ytdown

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import com.example.ytdown.services.ObservabilityService
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

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

        coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            observabilityService.trackError(
                "CoroutineException",
                throwable.message ?: "Unhandled coroutine exception",
                throwable
            )
        }

        // Observabilidade global para falhas em threads normais.
        // Nota: crashes antes de Application.onCreate() não são capturados por esse handler.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            observabilityService.trackError(
                "UncaughtException",
                throwable.message ?: "Application crashed without message",
                throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Observa o ciclo de vida do processo do aplicativo
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // App foi para o background total
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Limpeza final de recursos críticos
                playerManager.pause()
            }
        })
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onTerminate() {
        playerManager.pause()
        super.onTerminate()
    }
}
