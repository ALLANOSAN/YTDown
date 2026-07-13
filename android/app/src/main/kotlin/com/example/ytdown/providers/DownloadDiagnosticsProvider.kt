package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
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
     * Delega ao [DownloadScheduler.retry] para evitar duplicar a lógica de
     * reconstrução do WorkRequest (a mesma usada pela lista principal de downloads).
     */
    fun retryDownload(item: DownloadItemEntity) {
        viewModelScope.launch {
            // Envia um registro de diagnóstico ao Crashlytics antes de reprocessar.
            observabilityService.trackError(
                "DownloadDiagnostics",
                "retrying_failed_download: id=${item.id}, title=${item.title}, url=${item.url}, outputPath=${item.outputPath}, format=${item.format}, quality=${item.quality}, status=${item.status}"
            )

            scheduler.retry(item)
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
