package com.example.ytdown.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.*
import com.example.ytdown.core.domain.*
import com.example.ytdown.utils.CommonUtils
import com.example.ytdown.utils.TaskQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DownloadInputState(
    val urlInput: String = "",
    val fetchedItems: List<VideoPreviewItem> = emptyList(),
    val isFetching: Boolean = false,
    val fetchError: String? = null,
    val showDialog: Boolean = false,
    val artistInput: String = "",
    val albumInput: String = "",
    val selectedDownloadType: DownloadType = DownloadType.AUDIO,
    val selectedFormat: String = "mp3",
    val selectedQuality: String = "192",
    val isPlaylist: Boolean = false,
    val artworkUri: String? = null,
    val audioFormats: List<String> = listOf("mp3", "m4a", "flac", "opus", "ogg"),
    val videoFormats: List<String> = listOf("mp4", "mkv"),
    val audioBitrates: List<String> = listOf("128", "192", "256", "320"),
    val videoResolutions: List<String> = listOf("360p", "480p", "720p", "1080p", "best")
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val metadataManager: DownloadMetadataManager,
    private val scheduler: DownloadScheduler
) : ViewModel() {
    private val fetchQueue = TaskQueue(maxConcurrent = 1)

    // Regra 8: Máximo de 2 variáveis de instância.
    // _inputState encapsula todo o estado da UI de entrada
    private val _inputState = MutableStateFlow(DownloadInputState())
    val inputState = _inputState.asStateFlow()

    val downloads: StateFlow<List<DownloadItemEntity>> = scheduler.stream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlInputChanged(newUrl: String) {
        _inputState.update { it.copy(urlInput = CommonUtils.normalizeText(newUrl), fetchError = null) }
    }

    fun onArtworkSelected(uri: String) {
        _inputState.update { it.copy(artworkUri = uri) }
    }

    fun onSelectAllItems() {
        _inputState.update { state ->
            val items = state.fetchedItems.map { it.copy(isSelected = true) }
            state.copy(fetchedItems = items)
        }
    }

    fun onArtistInputChanged(newArtist: String) {
        _inputState.update { it.copy(artistInput = newArtist) }
    }

    fun onAlbumInputChanged(newAlbum: String) {
        _inputState.update { it.copy(albumInput = newAlbum) }
    }

    fun onDownloadTypeSelected(type: DownloadType) {
        _inputState.update { it.copy(selectedDownloadType = type) }
    }

    fun onFormatSelected(format: String) {
        _inputState.update { it.copy(selectedFormat = format) }
    }

    fun onQualitySelected(quality: String) {
        _inputState.update { it.copy(selectedQuality = quality) }
    }

    fun onVideoSelected(item: VideoPreviewItem, isSelected: Boolean) {
        _inputState.update { currentState ->
            val updatedItems = currentState.fetchedItems.map {
                if (it.url == item.url) it.copy(isSelected = isSelected) else it
            }
            currentState.copy(fetchedItems = updatedItems)
        }
    }

    fun fetchVideoDetails(context: Context, url: VideoUrl) {
        viewModelScope.launch {
            fetchQueue.add {
                performFetch(context, url)
            }.await()
        }
    }

    fun onDismissDialog() {
        _inputState.update { it.copy(showDialog = false) }
    }

    private suspend fun performFetch(context: Context, url: VideoUrl) {
        _inputState.update { it.copy(isFetching = true, fetchError = null, showDialog = false) }
        runCatching { metadataManager.fetchVideoInfo(context, url) }
            .onSuccess { updateStateWithInfo(it) }
            .onFailure { error -> _inputState.update { it.copy(isFetching = false, fetchError = error.message, showDialog = false) } }
    }

    private fun updateStateWithInfo(infoJson: VideoInfoJson) {
        val parsedEntries = metadataManager.parseEntries(infoJson)
        val first = parsedEntries.firstOrNull()

        _inputState.update { state ->
            state.copy(
                fetchedItems = parsedEntries,
                artistInput = first?.let { metadataManager.guessArtistFromTitle(it.title) } ?: "",
                albumInput = first?.let { metadataManager.guessAlbumFromTitle(it.title) } ?: "",
                isPlaylist = parsedEntries.size > 1,
                isFetching = false,
                showDialog = true
            )
        }
    }

    private fun selectItemsToDownload(state: DownloadInputState): List<VideoPreviewItem> {
        if (!state.isPlaylist) return state.fetchedItems
        return state.fetchedItems.filter { it.isSelected }
    }

    fun startDownloadFlow(folder: FilePath) {
        val currentState = _inputState.value
        val selectedItems = selectItemsToDownload(currentState)

        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            val baseMeta = MediaMetadata(
                title = MediaTitle(""), // Will be overwritten por cada item
                artist = ArtistName(currentState.artistInput),
                album = AlbumName(currentState.albumInput)
            )
            val downloadOptions = DownloadOptions(
                type = currentState.selectedDownloadType,
                format = currentState.selectedFormat,
                quality = currentState.selectedQuality
            )
            processEntries(selectedItems, folder, baseMeta, downloadOptions)
            _inputState.update { it.copy(fetchedItems = emptyList(), urlInput = "", showDialog = false) }
        }
    }

    private suspend fun processEntries(
        entries: List<VideoPreviewItem>,
        folder: FilePath,
        baseMeta: MediaMetadata,
        options: DownloadOptions
    ) {
        entries.forEach { (title, url) ->
            val finalMeta = baseMeta.copy(title = title)
            scheduler.schedule(url, folder, finalMeta, options)
        }
    }
}