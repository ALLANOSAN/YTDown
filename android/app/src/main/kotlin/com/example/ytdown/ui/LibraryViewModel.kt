package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.services.FileSystemScannerService
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
    private val folderService: com.example.ytdown.services.MusicFolderService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isScanning = MutableStateFlow(false)

    val selectedFolders = folderService.folders

    fun addFolder(uriString: String) {
        val path = resolvePhysicalPath(uriString) ?: uriString
        folderService.addFolder(path)
        performFullScan()
    }

    private fun resolvePhysicalPath(uriString: String): String? {
        // Tentativa simples de resolver caminhos comuns de documentos para File API
        // Ex: content://com.android.externalstorage.documents/tree/primary%3AMusic
        try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "content") {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                if (split[0] == "primary") {
                    return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                }
            }
        } catch (e: Exception) {
            // Falha ao resolver, retornamos o original
        }
        return null
    }

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
    }

    fun performFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            scannerService.fullSync()
            _isScanning.value = false
        }
    }
}
