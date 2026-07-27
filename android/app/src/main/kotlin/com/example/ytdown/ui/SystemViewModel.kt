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
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
            val artworkUrl = updatedSong.albumArtPath?.takeIf { it.isNotBlank() } ?: updatedSong.artistArtPath

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

    fun updateTrackArtist(song: DownloadItemEntity, newArtist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedSong = song.copy(artist = newArtist)
            databaseService.updateDownload(updatedSong)

            val targetPath = updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath
            val artworkUrl = updatedSong.albumArtPath?.takeIf { it.isNotBlank() } ?: updatedSong.artistArtPath

            downloadMetadataManager.rewriteMetadata(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    MediaTitle(updatedSong.title),
                    ArtistName(newArtist),
                    AlbumName(updatedSong.album.orEmpty())
                ),
                artworkUrl = artworkUrl
            )
        }
    }

    fun updateTrackAlbum(song: DownloadItemEntity, newAlbum: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedSong = song.copy(album = newAlbum)
            databaseService.updateDownload(updatedSong)

            val targetPath = updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath
            val artworkUrl = updatedSong.albumArtPath?.takeIf { it.isNotBlank() } ?: updatedSong.artistArtPath

            downloadMetadataManager.rewriteMetadata(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    MediaTitle(updatedSong.title),
                    ArtistName(updatedSong.artist.orEmpty()),
                    AlbumName(newAlbum)
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
    
    fun refreshYtDlpVersion(forceNetwork: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingUpdate = true) }
            try {
                val result = ytDlp.checkUpdate(
                    appFilesDir = context.filesDir.absolutePath,
                    forceRemote = forceNetwork
                )
                // Python retorna JSON: {"success": true, "current_version": "...", "latest_version": "...", "update_available": true}
                val json = org.json.JSONObject(result)
                val success = json.optBoolean("success", false)
                if (success) {
                    val current = json.optString("current_version", "?")
                    val latest = json.optString("latest_version", "?")
                    val updateAvailable = json.optBoolean("update_available", false)
                    _state.update {
                        it.copy(
                            ytDlpVersion = current,
                            latestVersion = latest,
                            isCheckingUpdate = false,
                            lastMessage = if (updateAvailable)
                                "Nova versão disponível: $latest" else "yt-dlp está atualizado ($current)"
                        )
                    }
                } else {
                    val error = json.optString("error", "Erro desconhecido")
                    _state.update {
                        it.copy(isCheckingUpdate = false, lastMessage = "Erro: $error")
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCheckingUpdate = false, lastMessage = "Erro ao verificar: ${e.message}")
                }
            }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true) }
            try {
                val result = ytDlp.performUpdate(context.filesDir.absolutePath)
                // Python retorna JSON: {"success": true, "updated": true, "current_version": "...", "message": "..."}
                val json = org.json.JSONObject(result)
                val success = json.optBoolean("success", false)
                val updated = json.optBoolean("updated", false)
                val message = json.optString("message", "")
                val newVersion = json.optString("current_version", _state.value.ytDlpVersion)
                _state.update {
                    it.copy(
                        isUpdating = false,
                        ytDlpVersion = newVersion,
                        lastMessage = if (success && updated) "yt-dlp atualizado para $newVersion!"
                            else if (success && !updated) "yt-dlp já está atualizado"
                            else "Erro: $message"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isUpdating = false, lastMessage = "Erro ao atualizar: ${e.message}")
                }
            }
        }
    }

    fun repairAllMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true) }
            val (repaired, failed, skipped) = metadataRepairer.repairAll { progress, message ->
                _state.update { it.copy(repairProgress = progress, lastMessage = message) }
            }
            _state.update { it.copy(
                isRepairing = false,
                lastMessage = "Reparo concluído: $repaired corrigidos, $skipped pulados, $failed falhas"
            ) }
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
