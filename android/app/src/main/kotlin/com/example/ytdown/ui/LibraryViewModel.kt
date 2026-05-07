package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.utils.HapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUIState(
    val songs: List<DownloadItemEntity> = emptyList(),
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val searchQuery: String = "",
    val isScanning: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val scannerService: FileSystemScannerService,
    private val folderService: com.example.ytdown.services.MusicFolderService,
    private val hapticManager: HapticManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isScanning = MutableStateFlow(false)

    val selectedFolders = folderService.folders
    val recentSearches: StateFlow<List<String>> = libraryRepository.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyAdded: StateFlow<List<DownloadItemEntity>> = libraryRepository.getRecentlyAdded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun triggerHapticSelection() = hapticManager.selection()
    fun triggerHapticClick() = hapticManager.impactLight()
    fun triggerHapticHeavy() = hapticManager.impactHeavy()
    fun triggerHapticMedium() = hapticManager.impactMedium()

    fun addFolder(uriString: String) {
        folderService.addFolder(uriString)
        performFullScan()
    }

    // resolvePhysicalPath foi removido pois agora o FileSystemScannerService
    // suporta nativamente URIs do SAF via DocumentFile, que é mais robusto
    // para versões modernas do Android (Scoped Storage).

    fun removeFolder(path: String) {
        folderService.removeFolder(path)
        performFullScan()
    }

    /**
     * O Cérebro da Biblioteca: Combina o banco de dados com a busca e o estado de scanner.
     * Migrado do Flutter (LibraryNotifier.dart).
     */
    val uiState: StateFlow<LibraryUIState> = combine(
        downloadRepository.stream(),
        _searchQuery,
        _isScanning
    ) { allDownloads, query, scanning ->
        val filtered = allDownloads.filter { it.status == "completed" }
            .filter { it.title.contains(query, ignoreCase = true) || it.artist?.contains(query, ignoreCase = true) == true }
        
        LibraryUIState(
            songs = filtered,
            artists = filtered.mapNotNull { it.artist }.distinct().sorted(),
            albums = filtered.mapNotNull { it.album }.distinct().sorted(),
            searchQuery = query,
            isScanning = scanning
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUIState())

    init {
        // Realiza um scan inicial silencioso ao abrir a biblioteca
        performFullScan()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank() && query.length > 2) {
            viewModelScope.launch {
                libraryRepository.saveSearch(query)
            }
        }
    }

    fun deleteSearch(query: String) {
        viewModelScope.launch {
            libraryRepository.deleteSearch(query)
        }
    }

    fun performFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            scannerService.fullSync()
            _isScanning.value = false
        }
    }
}
