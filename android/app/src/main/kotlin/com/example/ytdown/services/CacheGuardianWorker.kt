package com.example.ytdown.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class CacheGuardianWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val cacheDir = applicationContext.cacheDir
        val artworkDir = File(cacheDir, "artwork_cache")
        
        if (!artworkDir.exists()) return Result.success()

        val now = System.currentTimeMillis()
        val thirtyDaysInMillis = TimeUnit.DAYS.toMillis(30)

        artworkDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > thirtyDaysInMillis) {
                file.delete()
            }
        }

        return Result.success()
    }
}
