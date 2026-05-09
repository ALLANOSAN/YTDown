package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.ArtworkEnricher
import com.example.ytdown.core.business.LibraryExporter
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.business.MetadataRepairer
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.services.LyricsService
import com.example.ytdown.services.MetalArchivesService
import com.example.ytdown.services.MusicFolderService
import com.example.ytdown.services.ProgressBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SystemScreenState(
    val folders: List<String> = emptyList(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val isRepairing: Boolean = false,
    val repairProgress: Float = 0f,
    val isUpdating: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val ytDlpVersion: String = "...",
    val latestVersion: String = "...",
    val lastMessage: String? = null
)

@HiltViewModel
class SystemViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val folderService: MusicFolderService,
    private val scannerService: FileSystemScannerService,
    private val databaseService: DatabaseService,
    private val downloadMetadataManager: DownloadMetadataManager,
    private val metalArchivesService: MetalArchivesService,
    private val lyricsService: LyricsService,
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

    val playlists = libraryRepository.getPlaylists()

    init {
        viewModelScope.launch {
            folderService.folders.collect { folders ->
                _state.update { it.copy(folders = folders.toList()) }
            }
        }
        refreshYtDlpVersion(forceNetwork = false)
    }

    // ─── Pastas ───────────────────────────────────────────────────────────────
    fun addFolder(path: String) { viewModelScope.launch { folderService.addFolder(path) } }
    fun removeFolder(path: String) { viewModelScope.launch { folderService.removeFolder(path) } }

    // ─── yt-dlp versão / atualização ─────────────────────────────────────────
    fun refreshYtDlpVersion(forceNetwork: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingUpdate = true) }
            try {
                val result = ytDlp.checkUpdate(orchestrator.getAppFilesDir(), forceNetwork)
                val current = _state.value.ytDlpVersion
                val isNewer = result.isNotBlank() && result != current && current != "..."
                _state.update {
                    it.copy(
                        isCheckingUpdate = false,
                        latestVersion = result.ifBlank { it.latestVersion },
                        lastMessage = if (isNewer) "Nova versão disponível: $result" else it.lastMessage
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingUpdate = false) }
            }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isUpdating = true, lastMessage = null) }
            try {
                val result = ytDlp.performUpdate(orchestrator.getAppFilesDir())
                _state.update {
                    it.copy(
                        isUpdating = false,
                        ytDlpVersion = result.ifBlank { it.ytDlpVersion },
                        lastMessage = if (result.isNotBlank()) "Atualizado para v$result" else "Atualização concluída"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isUpdating = false, lastMessage = "Erro ao atualizar: ${e.message}") }
            }
        }
    }

    // ─── Exportação ───────────────────────────────────────────────────────────
    fun exportAllToPublicFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExporting = true, exportProgress = 0f) }
            try {
                val result = libraryExporter.exportAll { progress, _ ->
                    _state.update { it.copy(exportProgress = progress) }
                }
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        lastMessage = "Exportação concluída: ${result.first} exportados, ${result.second} falhas."
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, lastMessage = "Erro na exportação: ${e.message}") }
            }
        }
    }

    // ─── Reparo de metadados ──────────────────────────────────────────────────
    fun repairAllMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f) }
            try {
                val result = metadataRepairer.repairAll { progress, _ ->
                    _state.update { it.copy(repairProgress = progress) }
                }
                _state.update {
                    it.copy(
                        isRepairing = false,
                        lastMessage = "Reparo concluído: ${result.first} corrigidos, ${result.second} falhas."
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isRepairing = false, lastMessage = "Erro no reparo: ${e.message}") }
            }
        }
    }

    // ─── Enriquecimento de artwork ────────────────────────────────────────────
    fun enrichAllArtwork() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRepairing = true, repairProgress = 0f) }
            try {
                val result = artworkEnricher.enrichAll { progress, _ ->
                    _state.update { it.copy(repairProgress = progress) }
                }
                _state.update {
                    it.copy(
                        isRepairing = false,
                        lastMessage = "Capas atualizadas: ${result.first} ok, ${result.second} falhas, ${result.third} sem mudança."
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isRepairing = false, lastMessage = "Erro ao enriquecer capas: ${e.message}") }
            }
        }
    }

    // ─── Playlists ────────────────────────────────────────────────────────────
    fun createPlaylist(name: String) {
        viewModelScope.launch { libraryRepository.createPlaylist(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { libraryRepository.deletePlaylist(id) }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { databaseService.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { databaseService.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun getPlaylistTracksFlow(playlistId: String) = libraryRepository.getPlaylistTracks(playlistId)

    // ─── Tags / metadados individuais ────────────────────────────────────────
    fun updateAlbumBatch(artist: String? = null, oldAlbum: String, newAlbum: String, photo: String?) {
        viewModelScope.launch { libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo) }
    }

    fun updateArtistBatch(oldName: String, newName: String, photo: String?) {
        viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) }
    }

    // ✅ FIX: roda em IO para evitar ANR — ytDlp.rewriteMetadata() é bloqueante (Chaquo Python)
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

    fun superFixID3(song: DownloadItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artistName = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown"

                // 1. Metal-Archives: gênero e foto oficial da banda
                val response = metalArchivesService.getBandDetails(artistName)
                val maImageUrl = response.image_url
                    ?.takeIf { it.isNotBlank() && !it.contains("google.com") }

                // 2. Fallback: ArtworkManager → LastFM → iTunes → Deezer
                val artistImage = maImageUrl
                    ?: artworkEnricher.getArtistImageFor(artistName)
                    ?: song.artistImageUrl

                // 3. Capa do álbum via ArtworkManager
                val albumImage = song.album?.takeIf { it.isNotBlank() }?.let {
                    artworkEnricher.getAlbumCoverFor(artistName, it)
                } ?: song.albumImageUrl

                // 4. Arte final para embed: álbum > artista
                val artworkUrl = albumImage ?: artistImage ?: song.thumbnailPath

                val updatedSong = song.copy(
                    artistImageUrl = artistImage ?: song.artistImageUrl,
                    albumImageUrl = albumImage ?: song.albumImageUrl
                )
                databaseService.updateDownload(updatedSong)

                val targetPath = updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath
                downloadMetadataManager.rewriteMetadata(
                    path = FilePath(targetPath),
                    metadata = MediaMetadata(
                        MediaTitle(updatedSong.title),
                        ArtistName(updatedSong.artist.orEmpty()),
                        AlbumName(updatedSong.album.orEmpty())
                    ),
                    artworkUrl = artworkUrl
                )
            } catch (e: Exception) {
                android.util.Log.e("SystemViewModel", "Erro no SuperFix ID3: ${e.message}")
            }
        }
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────
    fun performFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            scannerService.fullSync()
            _isScanning.value = false
        }
    }
}
