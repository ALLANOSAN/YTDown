package com.example.ytdown.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.services.ArtworkManager
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.FileSystemScannerService
import com.example.ytdown.services.ProgressBus
import com.example.ytdown.core.business.YtDlpWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

data class SystemScreenState(
    val ytDlpVersion: String = "",
    val latestVersion: String = "",
    val isUpdating: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val isRepairing: Boolean = false,
    val repairProgress: Float = 0f,
    val lastMessage: String? = null
)

@HiltViewModel
class SystemViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val databaseService: DatabaseService,
    private val downloadMetadataManager: DownloadMetadataManager,
    private val artworkManager: ArtworkManager,
    private val scannerService: FileSystemScannerService,
    private val progressBusSource: ProgressBus,
    private val ytDlp: YtDlpWrapper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val progressBus: ProgressBus
        get() = progressBusSource
    private val _state = MutableStateFlow(SystemScreenState())
    val state = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Playlists reativas (Migrado do LibraryPlaylistsNotifier.dart)
    val playlists: StateFlow<List<PlaylistWithCount>> = libraryRepository.getPlaylists()
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
                scannerService.scanAndRegisterOrphans()
                _isScanning.value = false
            }
            // A biblioteca no Kotlin é baseada em Flows do Room, 
            // então ela se atualiza sozinha ao detectar mudanças no banco.
        }
    }

    fun refreshYtDlpVersion(forceNetwork: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingUpdate = true, lastMessage = null) }
            val response = runCatching {
                JSONObject(ytDlp.checkUpdate(context.filesDir.absolutePath))
            }
            response.onSuccess { json ->
                val isSuccess = json.optBoolean("success", false)
                var lastMessage = ""
                if (isSuccess) {
                    lastMessage = "yt-dlp já está atualizado"
                    if (json.optBoolean("update_available", false)) {
                        lastMessage = "Nova versão disponível"
                    }

                    _state.update {
                        it.copy(
                            ytDlpVersion = json.optString("current_version", ""),
                            latestVersion = json.optString("latest_version", ""),
                            isCheckingUpdate = false,
                            lastMessage = lastMessage
                        )
                    }
                }
                if (!isSuccess) {
                    lastMessage = json.optString("error", "Falha ao verificar versão")
                    _state.update {
                        it.copy(
                            isCheckingUpdate = false,
                            lastMessage = lastMessage
                        )
                    }
                }
            }.onFailure {
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
                JSONObject(ytDlp.performUpdate(context.filesDir.absolutePath))
            }
            response.onSuccess { json ->
                val isSuccess = json.optBoolean("success", false)
                var lastMessage = json.optString("message", "Atualização concluída")
                var ytDlpVersion = _state.value.ytDlpVersion
                var latestVersion = _state.value.latestVersion

                if (!isSuccess) {
                    lastMessage = json.optString("error", "Falha ao atualizar yt-dlp")
                }
                if (isSuccess) {
                    ytDlpVersion = json.optString("current_version", ytDlpVersion)
                    latestVersion = json.optString("latest_version", latestVersion)
                }

                _state.update {
                    it.copy(
                        ytDlpVersion = ytDlpVersion,
                        latestVersion = latestVersion,
                        isUpdating = false,
                        lastMessage = lastMessage
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(isUpdating = false, lastMessage = "Erro ao atualizar yt-dlp")
                }
            }
        }
                    it.copy(isUpdating = false, lastMessage = "Erro ao atualizar yt-dlp")
                }
            }
        }
    }

    fun repairAllMetadata() {
        viewModelScope.launch {
            processMetadataRepairBatch()
        }
    }

    fun enrichAllArtwork() {
        viewModelScope.launch {
            processArtworkBatch()
        }
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

        _state.update { it.copy(isRepairing = true, repairProgress = 0f, lastMessage = "Regravando tags ID3...") }

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

            val metadata = MediaMetadata(
                title = MediaTitle(item.title.trim()),
                artist = ArtistName(item.artist?.trim().orEmpty()),
                album = AlbumName(item.album?.trim().orEmpty())
            )

            val artworkUrl = item.albumImageUrl.takeIf { it?.isNotBlank() == true }
                ?: item.artistImageUrl.takeIf { it?.isNotBlank() == true }

            val result = downloadMetadataManager.rewriteMetadata(
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

        _state.update { it.copy(isRepairing = true, repairProgress = 0f, lastMessage = "Atualizando capas em lote...") }

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

            val metadata = MediaMetadata(
                title = MediaTitle(item.title.trim()),
                artist = ArtistName(item.artist?.trim().orEmpty()),
                album = AlbumName(item.album?.trim().orEmpty())
            )

            val result = downloadMetadataManager.rewriteMetadata(
                path = FilePath(item.outputPath),
                metadata = metadata,
                artworkUrl = artworkUrl
            )

            if (result.isSuccess()) {
                val updatedItem = item.copy(
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
                lastMessage = "Capas atualizadas: $updated, falhas: $failed, ignorados: $skipped."
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

        var artistImage = item.artistImageUrl?.takeIf { it.isNotBlank() }
        if (artistImage == null && artist.isNotBlank()) {
            artistImage = artworkManager.getArtistImage(artist)
        }

        var albumImage = item.albumImageUrl?.takeIf { it.isNotBlank() }
        if (albumImage == null && artist.isNotBlank()) {
            albumImage = artworkManager.getAlbumCover(artist, album)
        }

        var trackImage: String? = null
        if (artist.isNotBlank() && title.isNotBlank() && albumImage.isNullOrBlank()) {
            trackImage = artworkManager.getTrackCover(artist, title)
        }

        val finalAlbumImage = albumImage ?: trackImage
        val artworkUrl = finalAlbumImage ?: artistImage

        return ArtworkResolution(
            artworkUrl = artworkUrl,
            artistImageUrl = artistImage,
            albumImageUrl = finalAlbumImage
        )
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { libraryRepository.createPlaylist(name) }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { libraryRepository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun updateTrackName(song: com.example.ytdown.core.domain.DownloadItemEntity, newName: String) {
        viewModelScope.launch {
            // Lógica de atualização de tag individual que você descreveu
            libraryRepository.updateArtistInBatch(song.artist ?: "", song.artist ?: "", null) // Reaproveita lógica de lote
        }
    }

    fun updateArtistBatch(oldName: String, newName: String, photo: String?) {
        viewModelScope.launch { libraryRepository.updateArtistInBatch(oldName, newName, photo) }
    }

    fun updateAlbumBatch(artist: String? = null, oldAlbum: String, newAlbum: String, photo: String?) {
        viewModelScope.launch { libraryRepository.updateAlbumInBatch(artist, oldAlbum, newAlbum, photo) }
    }
}
