package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ytdown.core.business.*
import com.example.ytdown.core.domain.*
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

    // Regra 3: Uso de classes de domínio para parâmetros
    fun requestDownload(url: VideoUrl, outputPath: FilePath, metadata: MediaMetadata) {
        val id = UUID.randomUUID().toString()
        val item = DownloadItemEntity(
            id = id, 
            url = url.value, 
            title = metadata.title.value, 
            filePath = outputPath.value, 
            status = "pending", 
            progress = 0.0,
            artist = metadata.artist.value,
            album = metadata.album.value
        )
        
        viewModelScope.launch {
            repository.persist(item)
            enqueueWorker(id, url, outputPath, metadata)
        }
    }

    private fun enqueueWorker(id: String, url: VideoUrl, path: FilePath, meta: MediaMetadata) {
        val data = Data.Builder().apply {
            putString("VIDEO_ID", id)
            putString("VIDEO_URL", url.value)
            putString("OUTPUT_PATH", path.value)
            putString("TITLE", meta.title.value)
            putString("ARTIST", meta.artist.value)
            putString("ALBUM", meta.album.value)
        }.build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        workManager.enqueue(request)
    }
}