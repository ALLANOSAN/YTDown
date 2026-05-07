package com.example.ytdown.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.*
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.domain.StorageMediaType
import com.example.ytdown.core.domain.StorageMimeType
import com.example.ytdown.core.domain.StoragePath
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.services.ArtworkManager
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.services.ProgressBus
import com.example.ytdown.services.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
        private val downloadMetadataManager: DownloadMetadataManager,
        private val artworkManager: ArtworkManager,
        private val scannerService: FileSystemScannerService,
        private val progressBusSource: ProgressBus,
        private val ytDlp: YtDlpWrapper,
        @param:ApplicationContext private val context: Context
) : ViewModel() {

    val progressBus: ProgressBus
        get() = progressBusSource
    private val _state = MutableStateFlow(SystemScreenState())
    val state = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Playlists reativas (Migrado do LibraryPlaylistsNotifier.dart)
    val playlists: StateFlow<List<PlaylistWithCount>> =
            libraryRepository
                    .getPlaylists()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Migrado do Flutter: Escuta atualizações de download para atualizar a biblioteca
        viewModelScope.launch {
            progressBus.updates.collect { update ->
                if (update.status == "completed") {
                    refreshLibrary(forceScan = false)
                }
            }
        }

        // Scan inicial ao abrir o app
        refreshLibrary(forceScan = true)

        // Carrega versão atual / PyPI do yt-dlp
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
                
                android.util.Log.d(
                        "SystemViewModel",
                        "Sync: $registered registrados, $removed removidos"
                )
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
            response
                    .onSuccess { json ->
                        val isSuccess = json.optBoolean("success", false)
                        if (!isSuccess) {
                            val errorMsg = json.optString("error", "Falha ao verificar versão")
                            _state.update { it.copy(isCheckingUpdate = false, lastMessage = errorMsg) }
                            return@onSuccess
                        }

                        _state.update {
                            it.copy(
                                    ytDlpVersion = json.optString("current_version", ""),
                                    latestVersion = json.optString("latest_version", ""),
                                    isCheckingUpdate = false,
                                    lastMessage = if (json.optBoolean("update_available", false)) "Nova versão disponível" else "yt-dlp já está atualizado"
                            )
                        }
                    }
                    .onFailure {
                        _state.update {
                            it.copy(
                                    isCheckingUpdate = false,
                                    lastMessage = "Erro ao buscar versão do yt-dlp"
                            )
                        }
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
            response
                    .onSuccess { json ->
                        val isSuccess = json.optBoolean("success", false)
                        if (!isSuccess) {
                            val errorMsg = json.optString("error", "Falha ao atualizar yt-dlp")
                            _state.update { it.copy(isUpdating = false, lastMessage = errorMsg) }
                            return@onSuccess
                        }

                        var ytDlpVersion = _state.value.ytDlpVersion
                        val latestVersion = json.optString("latest_version", _state.value.latestVersion)
                        
                        ytDlpVersion = json.optString("current_version", ytDlpVersion)
                        if (ytDlpVersion == "unknown") {
                            ytDlpVersion = latestVersion
                        }

                        _state.update {
                            it.copy(
                                    ytDlpVersion = ytDlpVersion,
                                    latestVersion = latestVersion,
                                    isUpdating = false,
                                    lastMessage = json.optString("message", "Atualização concluída")
                            )
                        }
                    }
                    .onFailure {
                        _state.update {
                            it.copy(isUpdating = false, lastMessage = "Erro ao atualizar yt-dlp")
                        }
                    }
        }
    }

    fun repairAllMetadata() {
        viewModelScope.launch { processMetadataRepairBatch() }
    }

    fun enrichAllArtwork() {
        viewModelScope.launch { processArtworkBatch() }
    }

    private suspend fun processMetadataRepairBatch() {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) {
            _state.update {
                it.copy(
                        isRepairing = false,
                        repairProgress = 1f,
                        lastMessage = "Nenhum áudio concluído encontrado para reparo."
                )
            }
            return
        }

        _state.update {
            it.copy(isRepairing = true, repairProgress = 0f, lastMessage = "Regravando tags ID3...")
        }

        var repaired = 0
        var failed = 0
        var processed = 0

        for (item in items) {
            processed++
            _state.update { it.copy(repairProgress = processed / items.size.toFloat()) }

            if (item.outputPath.isBlank()) {
                failed++
                continue
            }

            val file = File(item.outputPath)
            if (!file.exists()) {
                failed++
                continue
            }

            val metadata =
                    MediaMetadata(
                            title = MediaTitle(item.title.trim()),
                            artist = ArtistName(item.artist?.trim().orEmpty()),
                            album = AlbumName(item.album?.trim().orEmpty())
                    )

            val artworkUrl =
                    item.albumImageUrl.takeIf { it?.isNotBlank() == true }
                            ?: item.artistImageUrl.takeIf { it?.isNotBlank() == true }

            val result =
                    downloadMetadataManager.rewriteMetadata(
                            path = FilePath(item.outputPath),
                            metadata = metadata,
                            artworkUrl = artworkUrl
                    )

            if (result.isSuccess()) {
                repaired++
            }
            if (!result.isSuccess()) {
                failed++
            }
        }

        _state.update {
            it.copy(
                    isRepairing = false,
                    repairProgress = 1f,
                    lastMessage = "Reparo concluído: $repaired itens regravados, $failed falhas."
            )
        }
    }

    private suspend fun processArtworkBatch() {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) {
            _state.update {
                it.copy(
                        isRepairing = false,
                        repairProgress = 1f,
                        lastMessage = "Nenhum áudio concluído encontrado para atualização de capas."
                )
            }
            return
        }

        _state.update {
            it.copy(
                    isRepairing = true,
                    repairProgress = 0f,
                    lastMessage = "Atualizando capas em lote..."
            )
        }

        var updated = 0
        var failed = 0
        var skipped = 0
        var processed = 0

        for (item in items) {
            processed++
            _state.update { it.copy(repairProgress = processed / items.size.toFloat()) }

            if (item.outputPath.isBlank()) {
                failed++
                continue
            }

            val file = File(item.outputPath)
            if (!file.exists()) {
                failed++
                continue
            }

            val resolution = resolveArtworkForItem(item)
            val artworkUrl = resolution.artworkUrl
            if (artworkUrl.isNullOrBlank()) {
                skipped++
                continue
            }

            val metadata =
                    MediaMetadata(
                            title = MediaTitle(item.title.trim()),
                            artist = ArtistName(item.artist?.trim().orEmpty()),
                            album = AlbumName(item.album?.trim().orEmpty())
                    )

            val result =
                    downloadMetadataManager.rewriteMetadata(
                            path = FilePath(item.outputPath),
                            metadata = metadata,
                            artworkUrl = artworkUrl
                    )

            if (result.isSuccess()) {
                val updatedItem =
                        item.copy(
                                artistImageUrl = resolution.artistImageUrl ?: item.artistImageUrl,
                                albumImageUrl = resolution.albumImageUrl ?: item.albumImageUrl
                        )
                databaseService.updateDownload(updatedItem)
                updated++
            }
            if (!result.isSuccess()) {
                failed++
            }
        }

        _state.update {
            it.copy(
                    isRepairing = false,
                    repairProgress = 1f,
                    lastMessage =
                            "Capas atualizadas: $updated, falhas: $failed, ignorados: $skipped."
            )
        }
    }

    private data class ArtworkResolution(
            val artworkUrl: String?,
            val artistImageUrl: String?,
            val albumImageUrl: String?
    )

    private suspend fun resolveArtworkForItem(item: DownloadItemEntity): ArtworkResolution {
        val artist = item.artist?.trim().orEmpty()
        val album = item.album?.trim().takeIf { !it.isNullOrBlank() } ?: "YTDown"
        val title = item.title.trim()

        val artistImage = if (artist.isNotBlank()) artworkManager.getArtistImage(artist) else null
        val albumImage = if (artist.isNotBlank() && album != "YTDown") artworkManager.getAlbumCover(artist, album) else null
        val trackImage = if (artist.isNotBlank() && title.isNotBlank()) artworkManager.getTrackCover(artist, title) else null

        // Ordem de preferência: Imagem do artista > Capa do álbum > Capa da música
        val finalArtwork = artistImage ?: albumImage ?: trackImage

        return ArtworkResolution(
                artworkUrl = finalArtwork,
                artistImageUrl = artistImage,
                albumImageUrl = albumImage ?: trackImage
        )
    }

    fun getPlaylistTracksFlow(playlistId: String) = libraryRepository.getPlaylistTracks(playlistId)

    fun createPlaylist(name: String) {
        viewModelScope.launch { libraryRepository.createPlaylist(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { libraryRepository.deletePlaylist(id) }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { libraryRepository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { libraryRepository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun updateTrackName(song: com.example.ytdown.core.domain.DownloadItemEntity, newName: String) {
        viewModelScope.launch {
            val updatedSong = song.copy(title = newName)
            databaseService.updateDownload(updatedSong)

            val targetPath =
                    updatedSong.exportedPath?.takeIf { it.isNotBlank() } ?: updatedSong.outputPath

            val artworkUrl =
                    updatedSong.albumImageUrl?.takeIf { it.isNotBlank() }
                            ?: updatedSong.artistImageUrl

            downloadMetadataManager.rewriteMetadata(
                    path = FilePath(targetPath),
                    metadata =
                            MediaMetadata(
                                    title = MediaTitle(newName),
                                    artist = ArtistName(updatedSong.artist.orEmpty()),
                                    album = AlbumName(updatedSong.album.orEmpty())
                            ),
                    artworkUrl = artworkUrl
            )
        }
    }

    fun updateArtistBatch(oldName: String, newName: String, photo: String?) {
        viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) }
    }

    fun exportAllToPublicFolders() {
        viewModelScope.launch { processExportBatch() }
    }

    private suspend fun processExportBatch() {
        val items =
                databaseService.getAllDownloads().filter {
                    it.status == "completed" && it.outputPath.isNotBlank()
                }

        if (items.isEmpty()) {
            _state.update {
                it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        lastMessage = "Nenhum download concluído encontrado para exportar."
                )
            }
            return
        }

        _state.update {
            it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    lastMessage = "Exportando ${items.size} itens..."
            )
        }

        var exported = 0
        var skipped = 0
        var failed = 0
        var processed = 0

        for (item in items) {
            processed++
            _state.update { it.copy(exportProgress = processed / items.size.toFloat()) }

            val sourceFile = java.io.File(item.outputPath)
            if (!sourceFile.exists()) {
                failed++
                continue
            }

            val isAudio = item.type == 0
            val ext = item.outputPath.substringAfterLast('.', "").lowercase()

            val mimeType =
                    StorageMimeType(
                            when (ext) {
                                "mp3" -> "audio/mpeg"
                                "m4a" -> "audio/mp4"
                                "flac" -> "audio/flac"
                                "opus" -> "audio/opus"
                                "ogg" -> "audio/ogg"
                                "mp4" -> "video/mp4"
                                "mkv" -> "video/x-matroska"
                                else -> "application/octet-stream"
                            }
                    )

            val mediaType = if (isAudio) StorageMediaType("audio") else StorageMediaType("video")

            runCatching {
                StorageService.exportToPublicCollection(
                        context = context,
                        sourcePath = StoragePath(item.outputPath),
                        displayName = item.title.ifBlank { sourceFile.name },
                        mediaType = mediaType,
                        mimeType = mimeType,
                        allowUserInteractionFallback = false
                )
                exported++
            }
                    .onFailure { failed++ }
        }

        _state.update {
            it.copy(
                    isExporting = false,
                    exportProgress = 1f,
                    lastMessage =
                            "Exportação concluída: $exported exportados, $skipped ignorados, $failed falhas."
            )
        }
    }

    fun updateAlbumBatch(
            artist: String? = null,
            oldAlbum: String,
            newAlbum: String,
            photo: String?
    ) {
        viewModelScope.launch {
            libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo)
        }
    }
}
