package com.example.ytdown.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.*
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.domain.StorageMediaType
import com.example.ytdown.core.domain.StorageMimeType
import com.example.ytdown.core.domain.StoragePath
import com.example.ytdown.core.infrastructure.persistence.entities.FavoriteEntity
import com.example.ytdown.services.ArtworkManager
import com.example.ytdown.services.DownloadFeedService
import com.example.ytdown.services.ObservabilityService
import com.example.ytdown.services.StorageService
import com.example.ytdown.utils.CommonUtils
import com.example.ytdown.utils.TaskQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// FIX #9: Sealed class for proper error state exposed to UI
sealed class DownloadUiState {
    object Idle : DownloadUiState()
    object Loading : DownloadUiState()
    data class Success(val message: String = "") : DownloadUiState()
    data class Error(val message: String, val throwable: Throwable? = null) : DownloadUiState()
}

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
        val audioFormats: List<String> = listOf("mp3", "m4a", "flac", "opus", "ogg"),
        val videoFormats: List<String> = listOf("mp4", "mkv"),
        val audioBitrates: List<String> = listOf("128", "192", "256", "320", "lossless"),
        val videoResolutions: List<String> = listOf("360p", "480p", "720p", "1080p", "best")
)

data class DownloadListState(
        val searchQuery: String = "",
        val selectedTab: Int = 0,
        val isSelectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet()
)

@HiltViewModel
class DownloadViewModel
@Inject
constructor(
        private val metadataManager: DownloadMetadataManager,
        private val artworkManager: ArtworkManager,
        private val scheduler: DownloadScheduler,
        private val downloadFeedService: DownloadFeedService,
        private val libraryRepository: LibraryRepository,
        private val downloadRepository: DownloadRepository,
        private val observabilityService: ObservabilityService,
        // FIX #10: Inject StorageService via Hilt instead of calling companion object directly
        private val storageService: StorageService
) : ViewModel() {
    private val fetchQueue = TaskQueue(maxConcurrent = 1)

    private val _inputState = MutableStateFlow(DownloadInputState())
    val inputState = _inputState.asStateFlow()

    private val _listState = MutableStateFlow(DownloadListState())
    val listState = _listState.asStateFlow()

    // FIX #9: Expose UI state with proper Loading/Success/Error states
    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    /**
     * Flow de lista completa — usado pela LibraryScreen e PlaylistDetailScreen que precisam de
     * todos os downloads para agrupar por artista/álbum. Separado do `downloads` (PagingData) que é
     * só para a DownloadListScreen.
     */
    val allDownloads: StateFlow<List<DownloadItemEntity>> =
            downloadFeedService
                    .stream()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * ⚡ Pipeline Paging3 — 30 itens por página, filtro reativo por busca e aba. O sort já vem do
     * banco (ORDER BY createdAt DESC), evitando sort em memória. cachedIn(viewModelScope) garante
     * que a lista sobrevive a recomposições.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val downloads: Flow<PagingData<DownloadItemEntity>> =
            combine(
                            _listState
                                    .map { Pair(it.searchQuery, it.selectedTab) }
                                    .distinctUntilChanged(),
                            kotlinx.coroutines.flow.flowOf(Unit)
                    ) { (query, tab), _ ->
                        val typeFilter =
                                when (tab) {
                                    1 -> 0 // só áudio
                                    2 -> 1 // só vídeo
                                    else -> null // todos
                                }
                        Pair(query, typeFilter)
                    }
                    .flatMapLatest { (query, typeFilter) ->
                        downloadFeedService.streamPaged(query, typeFilter)
                    }
                    .cachedIn(viewModelScope)

    val recentSearches: StateFlow<List<String>> =
            libraryRepository
                    .getRecentSearches()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> =
            libraryRepository
                    .getFavorites()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Actions ---

    fun onUrlInputChanged(newUrl: String) {
        _inputState.update {
            it.copy(urlInput = CommonUtils.normalizeText(newUrl), fetchError = null)
        }
    }

    fun onArtistInputChanged(newArtist: String) {
        _inputState.update { it.copy(artistInput = newArtist) }
    }

    fun onAlbumInputChanged(newAlbum: String) {
        _inputState.update { it.copy(albumInput = newAlbum) }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch { libraryRepository.deleteSearch(query) }
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

    fun onSearchQueryChanged(query: String) {
        _listState.update { it.copy(searchQuery = query) }
    }

    fun onTabSelected(index: Int) {
        _listState.update { it.copy(selectedTab = index) }
    }

    fun selectAllDownloads() {
        val currentState = _listState.value
        val typeFilter = when (currentState.selectedTab) {
            1 -> 0 // áudio
            2 -> 1 // vídeo
            else -> null
        }
        val query = currentState.searchQuery.lowercase()

        val filteredIds = allDownloads.value.filter { item ->
            val matchesType = typeFilter == null || item.type == typeFilter
            val matchesQuery = query.isEmpty() || item.title.lowercase().contains(query) || 
                             (item.artist?.lowercase()?.contains(query) == true)
            matchesType && matchesQuery
        }.map { it.id }.toSet()

        _listState.update { state ->
            val newSelectedIds = if (state.selectedIds.containsAll(filteredIds) && filteredIds.isNotEmpty()) {
                emptySet()
            } else {
                filteredIds
            }
            state.copy(selectedIds = newSelectedIds, isSelectionMode = newSelectedIds.isNotEmpty())
        }
    }

    fun toggleItemSelection(id: String) {
        _listState.update { state ->
            val selectedIds = state.selectedIds.toMutableSet()
            val alreadySelected = selectedIds.contains(id)
            if (alreadySelected) {
                selectedIds.remove(id)
            }
            if (!alreadySelected) {
                selectedIds.add(id)
            }
            state.copy(selectedIds = selectedIds, isSelectionMode = selectedIds.isNotEmpty())
        }
    }

    fun onSelectAllItems() {
        _inputState.update { state ->
            if (!state.isPlaylist) return@update state
            state.copy(fetchedItems = state.fetchedItems.map { it.copy(isSelected = true) })
        }
    }

    fun onVideoSelected(item: VideoPreviewItem, selected: Boolean) {
        _inputState.update { state ->
            if (!state.isPlaylist) return@update state
            state.copy(
                    fetchedItems =
                            state.fetchedItems.map {
                                var candidate = it
                                if (it.id == item.id) {
                                    candidate = it.copy(isSelected = selected)
                                }
                                candidate
                            }
            )
        }
    }

    fun deleteSelectedDownloads() {
        val ids = _listState.value.selectedIds
        if (ids.isEmpty()) return

        viewModelScope.launch {
            ids.forEach { targetId -> downloadRepository.delete(targetId) }
            _listState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
        }
    }

    fun clearSelectionMode() {
        _listState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            downloadRepository.delete(id)
            // Lote 1.2: A exclusão é tratada automaticamente pelo Flow do Room
        }
    }

    fun exportDownload(context: Context, id: String) {
        viewModelScope.launch {
            val item = downloadRepository.find(id) ?: return@launch
            if (item.outputPath.isBlank()) return@launch

            var mimeType = StorageMimeType("application/octet-stream")
            when (item.outputPath.substringAfterLast('.', "").lowercase()) {
                "mp3" -> mimeType = StorageMimeType("audio/mpeg")
                "m4a" -> mimeType = StorageMimeType("audio/mp4")
                "flac" -> mimeType = StorageMimeType("audio/flac")
                "opus" -> mimeType = StorageMimeType("audio/opus")
                "ogg" -> mimeType = StorageMimeType("audio/ogg")
                "mp4" -> mimeType = StorageMimeType("video/mp4")
                "mkv" -> mimeType = StorageMimeType("video/x-matroska")
            }
            var mediaType = StorageMediaType("video")
            if (item.type == 0) mediaType = StorageMediaType("audio")
            // FIX #10: Use injected storageService instance instead of companion object
            storageService.exportToPublicCollection(
                    context = context,
                    sourcePath = StoragePath(item.outputPath),
                    displayName = item.title.ifBlank { File(item.outputPath).name },
                    mediaType = mediaType,
                    mimeType = mimeType,
                    allowUserInteractionFallback = true
            )
        }
    }

    fun updateDownloadMetadata(
            id: String,
            newTitle: String,
            newArtist: String?,
            newAlbum: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = downloadRepository.find(id) ?: return@launch
                downloadRepository.persist(
                        item.copy(title = newTitle, artist = newArtist, album = newAlbum)
                )
            } catch (e: Exception) {
                observabilityService.trackError("DownloadViewModel", "Error updating download metadata", e, mapOf("id" to id))
            }
        }
    }

    fun fetchVideoDetails(context: Context, url: VideoUrl) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.saveSearch(url.value)
            fetchQueue.add { performFetch(context, url) }.await()
        }
    }

    private suspend fun performFetch(context: Context, url: VideoUrl) {
        _inputState.update { it.copy(isFetching = true, fetchError = null, showDialog = false) }
        runCatching { metadataManager.fetchVideoInfo(url) }
                .onSuccess { updateStateWithInfo(it) }
                .onFailure { error ->
                    observabilityService.trackError(
                            "DownloadViewModel",
                            "fetchVideoDetails failure: ${error.message}",
                            error
                    )
                    _inputState.update {
                        it.copy(isFetching = false, fetchError = error.message, showDialog = false)
                    }
                }
    }

    private fun updateStateWithInfo(infoJson: VideoInfoJson) {
        val parsedEntries = metadataManager.parseEntries(infoJson)
        if (parsedEntries.isEmpty()) {
            _inputState.update {
                it.copy(
                        isFetching = false,
                        fetchError = "Não foi possível processar as informações retornadas.",
                        showDialog = false
                )
            }
            return
        }

        val first = parsedEntries.firstOrNull()
        val metadataPlaylist = metadataManager.isPlaylist(infoJson)
        val isPlaylist = metadataPlaylist && parsedEntries.isNotEmpty() || parsedEntries.size > 1

        val data = org.json.JSONObject(infoJson.value).optJSONObject("data")
        val uploaderName =
                data?.optString("uploader")?.takeIf { it.isNotBlank() }
                        ?: data?.optString("channel")?.takeIf { it.isNotBlank() }

        // Validamos se os campos explicitos de metadados chegaram.
        val artistField = data?.optString("artist").takeIf { !it.isNullOrBlank() }
        val albumField = data?.optString("album").takeIf { !it.isNullOrBlank() }

        val playlistTitle =
                data?.optString("playlist_title").takeIf { !it.isNullOrBlank() }
                        ?: data?.optString("playlist").takeIf { !it.isNullOrBlank() }
                                ?: data?.optString("title").takeIf { !it.isNullOrBlank() }
        val titleForGuess = first?.title?.value ?: ""

        val artistForSingle = artistField ?: uploaderName ?: ""

        val albumForSingle = albumField ?: ""

        val artistForPlaylist =
                artistField
                        ?: metadataManager.guessArtistFromTitle(playlistTitle ?: titleForGuess)
                                ?: uploaderName ?: ""

        val albumForPlaylist = albumField ?: playlistTitle ?: ""

        val artistInput = if (isPlaylist) artistForPlaylist else artistForSingle
        val albumInput = if (isPlaylist) albumForPlaylist else albumForSingle

        observabilityService.info(
                "DownloadViewModel",
                "updateStateWithInfo isPlaylist=$isPlaylist artistField=${artistField ?: "missing"} " +
                        "albumField=${albumField ?: "missing"} uploaderName=${uploaderName ?: "missing"} " +
                        "playlistTitle=${playlistTitle ?: "missing"}"
        )

        _inputState.update { state ->
            state.copy(
                    fetchedItems = parsedEntries,
                    artistInput = artistInput,
                    albumInput = albumInput,
                    isPlaylist = isPlaylist,
                    isFetching = false,
                    showDialog = true
            )
        }
    }

    fun clearFetchError() {
        _inputState.update { it.copy(fetchError = null) }
    }

    fun onDismissDialog() {
        _inputState.update { it.copy(showDialog = false) }
    }

    fun startDownloadFlow(folder: FilePath) {
        _uiState.value = DownloadUiState.Loading
        val currentState = _inputState.value
        val selectedItems =
                currentState.fetchedItems.filter { item ->
                    if (!currentState.isPlaylist) return@filter true
                    item.isSelected
                }

        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            try {
                val baseMeta =
                        MediaMetadata(
                                title = MediaTitle(""),
                                artist = ArtistName(currentState.artistInput),
                                album = AlbumName(currentState.albumInput)
                        )
                val downloadOptions =
                        DownloadOptions(
                                type = currentState.selectedDownloadType,
                                format = currentState.selectedFormat,
                                quality = currentState.selectedQuality
                        )

                var resolvedArtworkUrl: String? = null
                if (currentState.artistInput.isNotBlank()) {
                    resolvedArtworkUrl =
                            currentState.albumInput.takeIf { it.isNotBlank() }?.let {
                                artworkManager.getAlbumCover(currentState.artistInput, it)
                            }
                                    ?: artworkManager.getArtistImage(currentState.artistInput)
                }

                selectedItems.forEach { item ->
                    val finalMeta = baseMeta.copy(title = item.title)
                    scheduler.schedule(item.url, folder, finalMeta, downloadOptions, resolvedArtworkUrl)
                }
                _inputState.update {
                    it.copy(fetchedItems = emptyList(), urlInput = "", showDialog = false)
                }
                _uiState.value = DownloadUiState.Success(message = "${selectedItems.size} item(s) scheduled for download")
            } catch (e: Exception) {
                _uiState.value = DownloadUiState.Error(message = e.message ?: "Unknown error scheduling download", throwable = e)
                observabilityService.trackError("DownloadViewModel", "startDownloadFlow failed", e)
            }
        }
    }
}
