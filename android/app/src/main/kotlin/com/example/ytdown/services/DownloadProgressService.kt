package com.example.ytdown.services

import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadProgressService @Inject constructor(
    private val progressBus: ProgressBus
) {
    fun addUpdate(item: DownloadItemEntity) {
        val progress = (item.progress * 100).toInt().coerceIn(0, 100)
        CoroutineScope(Dispatchers.IO).launch {
            progressBus.sendUpdate(item.id, progress, item.status)
        }
    }
}
