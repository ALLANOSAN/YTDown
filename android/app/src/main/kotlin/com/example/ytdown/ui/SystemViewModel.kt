package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.*
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.MusicFolderService
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.services.MetalArchivesService
import com.example.ytdown.services.LyricsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemScreenState(
    val folders: List<String> = emptyList(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
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
    private val lyricsService: LyricsService
) : ViewModel() {

    private val _state = MutableStateFlow(SystemScreenState())
    val state = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    val playlists = libraryRepository.getPlaylists()

    init {
        viewModelScope.launch {
            folderService.folders.collect { folders ->
                _state.update { it.copy(folders = folders) }
            }
        }
    }

    fun addFolder(path: String) { viewModelScope.launch { folderService.addFolder(path) } }
    fun removeFolder(path: String) { viewModelScope.launch { folderService.removeFolder(path) } }

    fun exportLibrary(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExporting = true, exportProgress = 0f) }
            try {
                val songs = databaseService.getLibraryAudios()
                var exported = 0
                var failed = 0
                
                val exporter = com.example.ytdown.core.business.LibraryExporter(databaseService, context)
                val result = exporter.exportAll(context) { progress, _ -> 
                    _state.update { it.copy(exportProgress = progress) }
                }
                exported = result.first
                failed = result.second
                
                _state.update { 
                    it.copy(
                        isExporting = false, 
                        exportProgress = 1f, 
                        lastMessage = "Exportação concluída: $exported exportados, $failed falhas."
                    ) 
                }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, lastMessage = "Erro na exportação: ${e.message}") }
            }
        }
    }

    fun superFixID3(song: DownloadItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artistName = song.artist?.takeIf { it.isNotBlank() } ?: "Unknown"
                val response = metalArchivesService.getBandDetails(artistName)
                
                if (response.success) {
                    val artworkUrl = response.image_url ?: song.albumImageUrl ?: song.thumbnailPath

                    val updatedSong = song.copy(
                        artistImageUrl = artworkUrl
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
                }
            } catch (e: Exception) {
                android.util.Log.e("SystemViewModel", "Erro no SuperFix ID3: ${e.message}")
            }
        }
    }

    fun updateAlbumBatch(artist: String? = null, oldAlbum: String, newAlbum: String, photo: String?) { viewModelScope.launch { libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo) } }
    fun updateArtistBatch(oldName: String, newName: String, photo: String?) { viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) } }

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
}
