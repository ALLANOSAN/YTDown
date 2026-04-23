package com.example.ytdown.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.MetadataService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class SystemState(
    val ytDlpVersion: String = "Desconhecida",
    val latestVersion: String = "Desconhecida",
    val isCheckingUpdate: Boolean = false,
    val isUpdating: Boolean = false,
    val isRepairing: Boolean = false,
    val repairProgress: Float = 0f,
    val lastMessage: String? = null
)

@HiltViewModel
class SystemViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ytDlp: YtDlpWrapper,
    private val repository: DownloadRepository,
    private val metadataManager: DownloadMetadataManager,
    private val metadataService: MetadataService
) : ViewModel() {

    private val _state = MutableStateFlow(SystemState())
    val state = _state.asStateFlow()

    init {
        checkYtDlpUpdate()
    }

    fun checkYtDlpUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingUpdate = true) }
            val result = ytDlp.checkUpdate(context.filesDir.absolutePath)
            runCatching {
                val json = JSONObject(result)
                _state.update { it.copy(
                    ytDlpVersion = json.optString("current_version", "Desconhecida"),
                    latestVersion = json.optString("latest_version", "Desconhecida"),
                    isCheckingUpdate = false
                )}
            }.onFailure {
                _state.update { it.copy(isCheckingUpdate = false, lastMessage = "Erro ao verificar atualização") }
            }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true) }
            val result = ytDlp.performUpdate(context.filesDir.absolutePath)
            _state.update { it.copy(isUpdating = false, lastMessage = "Atualização concluída") }
            checkYtDlpUpdate()
        }
    }

    fun repairAllMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f) }
            val downloads = repository.all()
            val completed = downloads.filter { it.status == "completed" }
            
            completed.forEachIndexed { index, item ->
                metadataManager.rewriteMetadata(
                    FilePath(item.filePath),
                    MediaMetadata(MediaTitle(item.title), ArtistName(item.artist), AlbumName(item.album))
                )
                _state.update { it.copy(repairProgress = (index + 1).toFloat() / completed.size) }
            }
            
            _state.update { it.copy(isRepairing = false, lastMessage = "Reparo de metadados concluído") }
        }
    }

    fun enrichAllArtwork() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f) }
            val downloads = repository.all()
            val completed = downloads.filter { it.status == "completed" }
            
            completed.forEachIndexed { index, item ->
                val artMap = metadataService.getArtwork(item.artist, item.album, item.title)
                val updated = item.copy(
                    artistImageUrl = artMap["artistArt"] ?: item.artistImageUrl,
                    albumImageUrl = artMap["albumArt"] ?: item.albumImageUrl
                )
                if (updated != item) {
                    repository.persist(updated)
                }
                _state.update { it.copy(repairProgress = (index + 1).toFloat() / completed.size) }
            }
            
            _state.update { it.copy(isRepairing = false, lastMessage = "Enriquecimento de capas concluído") }
        }
    }
}
