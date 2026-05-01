package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.services.ObservabilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadDiagnosticsProvider @Inject constructor(
    private val repository: DownloadRepository,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver,
    private val observabilityService: ObservabilityService
) : ViewModel() {

    /**
     * Filtra apenas downloads que falharam para exibir na tela de diagnóstico.
     */
    val failedDownloads: StateFlow<List<DownloadItemEntity>> = repository.stream()
        .map { list -> list.filter { it.status == "failed" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Estatísticas rápidas para o cabeçalho do diagnóstico.
     */
    val diagnosticsStats = repository.stream()
        .map { list ->
            mapOf(
                "total" to list.size,
                "completed" to list.count { it.status == "completed" },
                "failed" to list.count { it.status == "failed" }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Tenta baixar novamente um item que falhou.
     */
    fun retryDownload(item: DownloadItemEntity) {
        viewModelScope.launch {
            val metadata = MediaMetadata(
                title = MediaTitle(item.title),
                artist = ArtistName(item.artist ?: ""),
                album = AlbumName(item.album ?: "")
            )
            var effectiveType = DownloadType.VIDEO
            if (item.type == 0) {
                effectiveType = DownloadType.AUDIO
            }
            val options = DownloadOptions(
                type = effectiveType,
                format = item.format,
                quality = item.quality
            )
            
            // Pega a pasta pai do caminho atual ou usa a pasta de downloads privados como fallback.
            var parentPath = storageResolver.privateDownloadsDir(isAudio = item.type == 0).absolutePath
            if (item.outputPath.isNotBlank()) {
                parentPath = item.outputPath.substringBeforeLast("/")
            }
            
            // Envia um registro de diagnóstico ao Crashlytics antes de reprocessar.
            observabilityService.trackError(
                "DownloadDiagnostics",
                "retrying_failed_download: id=${item.id}, title=${item.title}, url=${item.url}, outputPath=${item.outputPath}, format=${item.format}, quality=${item.quality}, status=${item.status}"
            )

            // Remove o antigo e agenda o novo
            repository.delete(item.id)
            scheduler.schedule(
                url = VideoUrl(item.url),
                path = FilePath(parentPath),
                meta = metadata,
                options = options
            )
        }
    }

    /**
     * Limpa todos os logs de falhas.
     */
    fun clearFailedLogs() {
        viewModelScope.launch {
            failedDownloads.value.forEach { item ->
                observabilityService.info(
                    "DownloadDiagnostics",
                    "clearing_failed_download_log: id=${item.id}, title=${item.title}, url=${item.url}, status=${item.status}, outputPath=${item.outputPath}"
                )
                repository.delete(item.id)
            }
        }
    }
}
