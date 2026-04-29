package com.example.ytdown.services

import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Barramento de Eventos de Progresso.
 * Migrado do Flutter (download_progress_service.dart).
 */
@Singleton
class ProgressBus @Inject constructor() {
    private val _updates = MutableSharedFlow<ProgressUpdate>(extraBufferCapacity = 64)
    val updates = _updates.asSharedFlow()

    suspend fun sendUpdate(id: String, progress: Int, status: String) {
        _updates.emit(ProgressUpdate(id, progress, status))
    }
}

data class ProgressUpdate(
    val id: String,
    val progress: Int,
    val status: String
)
