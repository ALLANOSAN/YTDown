package com.example.ytdown.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.*
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.persistence.entities.FavoriteEntity
import com.example.ytdown.services.DownloadFeedService
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.services.ProgressBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.domain.ArtistName
import com.example.ytdown.core.domain.AlbumName
import com.example.ytdown.core.domain.FilePath

data class SystemScreenState(
        val ytDlpVersion: String = "",
        val latestVersion: String = "",
        val isUpdating: Boolean = false,
        val isCheckingUpdate: Boolean = false,
        val isRepairing: Boolean = false,
        val repairProgress: Float = 0f,
        val lastMessage: String? = null,
        val isExporting: Boolean = false,
        val exportProgress: Float = 0f
)

@HiltViewModel
class SystemViewModel
@Inject
constructor(
        private val libraryRepository: LibraryRepository,
        private val databaseService: DatabaseService,
        private val scannerService: FileSystemScannerService,
        private val progressBusSource: ProgressBus,
        private val metadataRepairer: MetadataRepairer,
        private val libraryExporter: LibraryExporter,
        private val artworkEnricher: ArtworkEnricher,
        private val ytDlp: YtDlpWrapper,
        private val downloadMetadataManager: DownloadMetadataManager,
        @param:ApplicationContext private val context: Context
) : ViewModel() {

    val progressBus: ProgressBus
        get() = progressBusSource
    private val _state = MutableStateFlow(SystemScreenState())
    val state = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    val playlists: StateFlow<List<PlaylistWithCount>> =
            libraryRepository
                    .getPlaylists()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            progressBus.updates.collect { update ->
                if (update.status == "completed") {
                    refreshLibrary(forceScan = false)
                }
            }
        }
        refreshLibrary(forceScan = true)
        refreshYtDlpVersion(forceNetwork = false)
    }

    fun refreshLibrary(forceScan: Boolean) {
        viewModelScope.launch {
            if (forceScan) {
                _isScanning.value = true
                _state.update { it.copy(lastMessage = "Iniciando escaneamento...") }
                
                val (registered, removed) = scannerService.fullSync { msg ->
                    _state.update { it.copy(lastMessage = msg) }
                }
                
                _isScanning.value = false
                _state.update { it.copy(lastMessage = "Scan concluído: $registered novos, $removed removidos.") }
            }
        }
    }

    fun refreshYtDlpVersion(forceNetwork: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingUpdate = true, lastMessage = null) }
            val response = runCatching {
                withContext(Dispatchers.IO) {
                    JSONObject(ytDlp.checkUpdate(context.filesDir.absolutePath, forceNetwork))
                }
            }
            response.onSuccess { json ->
                val isSuccess = json.optBoolean("success", false)
                _state.update {
                    it.copy(
                        ytDlpVersion = json.optString("current_version", ""),
                        latestVersion = json.optString("latest_version", ""),
                        isCheckingUpdate = false,
                        lastMessage = if (!isSuccess) json.optString("error", "Falha ao verificar") else (if (json.optBoolean("update_available", false)) "Nova versão disponível" else "yt-dlp já está atualizado")
                    )
                }
            }.onFailure {
                _state.update { it.copy(isCheckingUpdate = false, lastMessage = "Erro ao buscar versão") }
            }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, lastMessage = null) }
            val response = runCatching {
                withContext(Dispatchers.IO) {
                    JSONObject(ytDlp.performUpdate(context.filesDir.absolutePath))
                }
            }
            response.onSuccess { json ->
                _state.update {
                    it.copy(
                        ytDlpVersion = json.optString("current_version", it.ytDlpVersion),
                        latestVersion = json.optString("latest_version", it.latestVersion),
                        isUpdating = false,
                        lastMessage = json.optString("message", "Atualização concluída")
                    )
                }
            }.onFailure {
                _state.update { it.copy(isUpdating = false, lastMessage = "Erro ao atualizar") }
            }
        }
    }

    fun repairAllMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f, lastMessage = "Iniciando reparo...") }
            
            val (repaired, failed) = metadataRepairer.repairAll { progress, msg ->
                _state.update { it.copy(repairProgress = progress, lastMessage = msg) }
            }

            _state.update {
                it.copy(
                        isRepairing = false,
                        repairProgress = 1f,
                        lastMessage = "Reparo concluído: $repaired itens regravados, $failed falhas."
                )
            }
        }
    }

    fun enrichAllArtwork() {
        viewModelScope.launch {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f, lastMessage = "Enriquecendo capas...") }
            
            val (updated, failed, skipped) = artworkEnricher.enrichAll { progress, msg ->
                _state.update { it.copy(repairProgress = progress, lastMessage = msg) }
            }

            _state.update {
                it.copy(
                        isRepairing = false,
                        repairProgress = 1f,
                        lastMessage = "Capas atualizadas: $updated, falhas: $failed, ignorados: $skipped."
                )
            }
        }
    }

    fun exportAllToPublicFolders() {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportProgress = 0f, lastMessage = "Iniciando exportação...") }
            
            val (exported, failed) = libraryExporter.exportAll { progress, msg ->
                _state.update { it.copy(exportProgress = progress, lastMessage = msg) }
            }

            _state.update {
                it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        lastMessage = "Exportação concluída: $exported exportados, $failed falhas."
                )
            }
        }
    }

    fun getPlaylistTracksFlow(playlistId: String) = libraryRepository.getPlaylistTracks(playlistId)
    
    fun createPlaylist(name: String) { viewModelScope.launch { libraryRepository.createPlaylist(name) } }
    fun deletePlaylist(id: String) { viewModelScope.launch { libraryRepository.deletePlaylist(id) } }
    fun removeTrackFromPlaylist(playlistId: String, trackId: String) { viewModelScope.launch { libraryRepository.removeTrackFromPlaylist(playlistId, trackId) } }
    fun addTrackToPlaylist(playlistId: String, trackId: String) { viewModelScope.launch { libraryRepository.addTrackToPlaylist(playlistId, trackId) } }
    fun updateAlbumBatch(artist: String? = null, oldAlbum: String, newAlbum: String, photo: String?) { viewModelScope.launch { libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo) } }
    fun updateArtistBatch(oldName: String, newName: String, photo: String?) { viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) } }

    fun updateTrackName(song: DownloadItemEntity, newName: String) {
        viewModelScope.launch {
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
}
