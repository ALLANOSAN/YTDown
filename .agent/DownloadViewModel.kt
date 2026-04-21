package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.work.DownloadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val downloads: StateFlow<List<DownloadItemEntity>> = repository.streamAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun requestDownload(url: String, outputPath: String) {
        val id = UUID.randomUUID().toString()
        val item = DownloadItemEntity(
            id = id, url = url, title = "Aguardando...", 
            filePath = outputPath, status = "pending", progress = 0.0
        )
        
        viewModelScope.launch {
            repository.persist(item)
            enqueueWorker(id, url, outputPath)
        }
    }

    private fun enqueueWorker(id: String, url: String, path: String) {
        val data = Data.Builder()
            .putString("VIDEO_ID", id)
            .putString("VIDEO_URL", url)
            .putString("OUTPUT_PATH", path)
            .build()

        workManager.enqueue(OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(data).build())
    }
}