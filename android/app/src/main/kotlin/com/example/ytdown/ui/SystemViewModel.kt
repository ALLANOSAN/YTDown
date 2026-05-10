package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.ArtworkEnricher
import com.example.ytdown.core.business.LibraryExporter
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.business.MetadataRepairer
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.core.infrastructure.work.MetadataFixWorker
import com.example.ytdown.services.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemScreenState(
    val folders: List<String> = emptyList(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val lastMessage: String? = null,
    val isRepairing: Boolean = false,
    val repairProgress: Float = 0f,
    val isUpdating: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val ytDlpVersion: String = "...",
    val latestVersion: String = "..."
)

@HiltViewModel
class SystemViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val folderService: MusicFolderService,
    private val scannerService: FileSystemScannerService,
    private val databaseService: DatabaseService,
    private val downloadMetadataManager: DownloadMetadataManager,
    private val musicBrainzService: MusicBrainzService,
    private val lyricsService: LyricsService,
    private val workManager: WorkManager,
    private val libraryExporter: LibraryExporter,
    private val metadataRepairer: MetadataRepairer,
    private val artworkEnricher: ArtworkEnricher,
    private val ytDlp: YtDlpWrapper,
    private val orchestrator: BinaryOrchestrator,
    val progressBus: ProgressBus
) : ViewModel() {

    private val _state = MutableStateFlow(SystemScreenState())
    val state = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // ✅ FIX: expõe o evento SAF para a UI registrar ActivityResultContracts.CreateDocument
    private val _safPickerRequest = MutableStateFlow<StorageService.Companion.SafPickerRequest?>(null)
    val safPickerRequest: StateFlow<StorageService.Companion.SafPickerRequest?> = _safPickerRequest.asStateFlow()

    val playlists = libraryRepository.getPlaylists()

    init {
        viewModelScope.launch {
            folderService.folders.collect { folders ->
                _state.update { it.copy(folders = folders.toList()) }
            }
        }
        // ✅ Coleta eventos SAF do StorageService e repassa para a UI
        viewModelScope.launch {
            StorageService.safPickerRequests.collect { request ->
                _safPickerRequest.value = request
            }
        }
        refreshYtDlpVersion(forceNetwork = false)
    }

    fun clearSafPickerRequest() { _safPickerRequest.value = null }

    // ─── Pastas ───────────────────────────────────────────────────────────────
    fun addFolder(path: String) { viewModelScope.launch { folderService.addFolder(path) } }
    fun removeFolder(path: String) { viewModelScope.launch { folderService.removeFolder(path) } }

    fun superFixID3(song: DownloadItemEntity? = null) {
        val inputData = if (song != null) {
            workDataOf("TARGET_ID" to song.id)
        } else {
            Data.EMPTY
        }

        val workRequest = OneTimeWorkRequestBuilder<com.example.ytdown.core.infrastructure.work.BatchMetadataFixWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            "batch_fix_metadata",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun updateAlbumBatch(artist: String? = null, oldAlbum: String, newAlbum: String, photo: String?) { 
        viewModelScope.launch { libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo) } 
    }
    fun updateArtistBatch(oldName: String, newName: String, photo: String?) { 
        viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) } 
    }

    fun updateTrackName(song: DownloadItemEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedSong = song.copy(title = newName)
            databaseService.updateDownload(updatedSong)

            val targetPath = updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath
            val artworkUrl = updatedSong.albumImageUrl?.takeIf { it.isNotBlank() } ?: updatedSong.artistImageUrl

            downloadMetadataManager.rewriteMetadata(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    MediaTitle(newName),
                    ArtistName(updatedSong.artist.orEmpty()),
                    AlbumName(updatedSong.album.orEmpty())
                ),
                artworkUrl = artworkUrl
            )
        }
    }

    fun performFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            scannerService.fullSync()
            _isScanning.value = false
        }
    }
    
    fun refreshYtDlpVersion(forceNetwork: Boolean) {}

    fun updateYtDlp() {}

    fun repairAllMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true) }
            metadataRepairer.repairAll { progress, message ->
                _state.update { it.copy(repairProgress = progress, lastMessage = message) }
            }
            _state.update { it.copy(isRepairing = false, lastMessage = "Reparo concluído") }
        }
    }

    fun exportAllToPublicFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            libraryExporter.exportAll { progress, message ->
                _state.update { it.copy(exportProgress = progress, lastMessage = message) }
            }
            _state.update { it.copy(isExporting = false, lastMessage = "Exportação concluída") }
        }
    }

    fun enrichAllArtwork() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true) }
            artworkEnricher.enrichAll { progress, message ->
                _state.update { it.copy(repairProgress = progress, lastMessage = message) }
            }
            _state.update { it.copy(isRepairing = false, lastMessage = "Enriquecimento concluído") }
        }
    }

    fun createPlaylist(name: String) { viewModelScope.launch { libraryRepository.createPlaylist(name) } }
    fun deletePlaylist(id: String) { viewModelScope.launch { libraryRepository.deletePlaylist(id) } }
    fun addTrackToPlaylist(playlistId: String, trackId: String) { viewModelScope.launch { libraryRepository.addTrackToPlaylist(playlistId, trackId) } }
    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { libraryRepository.removeTrackFromPlaylist(playlistId, trackId) }
    }
    fun getPlaylistTracksFlow(playlistId: String) = libraryRepository.getPlaylistTracks(playlistId)
}
