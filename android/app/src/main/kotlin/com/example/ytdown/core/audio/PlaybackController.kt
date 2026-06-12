package com.example.ytdown.core.audio

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.MediaPlaybackService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.apply
import kotlin.collections.map

/**
 * PlaybackUiState - ÚNICA fonte de verdade para o estado do player.
 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrack: DownloadItemEntity? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val repeatMode: Int = 0,
    val isShuffleEnabled: Boolean = false,
    val errorMessage: String? = null,
    val spectrumData: FloatArray = FloatArray(64)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaybackUiState) return false
        return isPlaying == other.isPlaying && isBuffering == other.isBuffering &&
                currentTrack == other.currentTrack && positionMs == other.positionMs &&
                durationMs == other.durationMs && volume == other.volume &&
                repeatMode == other.repeatMode && isShuffleEnabled == other.isShuffleEnabled &&
                errorMessage == other.errorMessage && spectrumData.contentEquals(other.spectrumData)
    }

    override fun hashCode(): Int {
        var result = isPlaying.hashCode()
        result = 31 * result + isBuffering.hashCode()
        result = 31 * result + (currentTrack?.hashCode() ?: 0)
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + volume.hashCode()
        result = 31 * result + repeatMode.hashCode()
        result = 31 * result + isShuffleEnabled.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + spectrumData.contentHashCode()
        return result
    }
}

/**
 * PlaybackController - SINGLE SOURCE OF TRUTH para todo o estado de reprodução.
 *
 * FLUXO CORRETO DO MEDIA3:
 * 1. MediaPlaybackService.onCreate() cria MediaSession com BassMediaSessionAdapter
 * 2. PlaybackController.connectMediaController() cria MediaController que se conecta ao serviço
 * 3. playPlaylist() alimenta o adapter e usa MediaController.play()
 * 4. Media3 inicia o serviço e mostra notificação AUTOMATICAMENTE
 * 5. BassMediaSessionAdapter.play() → actionDispatcher → BASS engine
 *
 * NÃO usar startService() ou startForegroundService() manualmente!
 */
@Singleton
class PlaybackController @Inject constructor(
    private val engineProvider: javax.inject.Provider<BassPlaybackEngine>,
    private val bassAdapterProvider: dagger.Lazy<BassMediaSessionAdapter>,
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "PlaybackController"
    }

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var playlist: List<DownloadItemEntity> = emptyList()
    private var currentIndex: Int = -1

    // MediaController - conecta ao MediaPlaybackService via Media3
    private var mediaController: MediaController? = null
    private var controllerConnected = false

    init {
        Log.e(TAG, "CONTROLLER CREATED - connecting MediaController")
        connectMediaController()
    }

    /**
     * Conecta ao MediaPlaybackService via MediaController (modo correto do Media3).
     * Quando o MediaController se conecta, o Media3 inicia o serviço automaticamente.
     */
    private fun connectMediaController() {
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, MediaPlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(context, sessionToken)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        Log.w(TAG, "⚠️ MediaController desconectado do service")
                        this@PlaybackController.mediaController = null
                        controllerConnected = false
                    }
                })
                .buildAsync()
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller
                    controllerConnected = true
                    Log.d(TAG, "✅ MediaController conectado ao MediaPlaybackService")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Falha ao obter MediaController: ${e.message}")
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao conectar MediaController: ${e.message}")
        }
    }

    fun updateState(transform: (PlaybackUiState) -> PlaybackUiState) {
        _uiState.update(transform)
    }

    // ========== Métodos de Atualização ==========

    fun updatePlaying(isPlaying: Boolean) = updateState { it.copy(isPlaying = isPlaying) }
    fun updateTrack(track: DownloadItemEntity?) = updateState { it.copy(currentTrack = track) }
    fun updatePosition(posMs: Long) = updateState { it.copy(positionMs = posMs) }
    fun updateDuration(durMs: Long) = updateState { it.copy(durationMs = durMs) }
    fun updateBuffering(isBuffering: Boolean) = updateState { it.copy(isBuffering = isBuffering) }
    fun updateVolume(volume: Float) = updateState { it.copy(volume = volume) }
    fun updateRepeatMode(mode: Int) = updateState { it.copy(repeatMode = mode) }
    fun updateShuffle(enabled: Boolean) = updateState { it.copy(isShuffleEnabled = enabled) }
    fun updateSpectrum(data: FloatArray) = updateState { it.copy(spectrumData = data) }
    fun setError(message: String?) = updateState { it.copy(errorMessage = message) }

    // ========== Playback Commands (via MediaController) ==========

    fun playTrack(track: DownloadItemEntity) {
        playPlaylist(listOf(track), 0)
    }

    fun playPlaylist(tracks: List<DownloadItemEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        this.playlist = tracks
        this.currentIndex = startIndex

        val adapter = bassAdapterProvider.get()

        // 1. Alimentar Media3 com a playlist (para notificação, metadata, etc.)
        adapter.setPlaylistFromEntities(tracks)

        // 2. Carregar e tocar no BASS engine
        engineProvider.get().play(tracks[startIndex])
        Log.d(TAG, "▶️ playPlaylist: ${tracks[startIndex].title}")

        // 3. Notificar Media3 via MediaController (para que a notificação apareça)
        val controller = mediaController
        if (controller != null && controllerConnected) {
            val mediaItems = tracks.map { it.toMedia3Item() }
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        val track = playlist[currentIndex]
        engineProvider.get().play(track)
        Log.d(TAG, "▶️ playNext: ${track.title}")
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        val track = playlist[currentIndex]
        engineProvider.get().play(track)
        Log.d(TAG, "⏮️ playPrevious: ${track.title}")
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller != null && controllerConnected) {
            if (_uiState.value.isPlaying) controller.pause() else controller.play()
        } else {
            val engine = engineProvider.get()
            if (_uiState.value.isPlaying) {
                engine.pause()
            } else {
                val track = currentTrack
                if (engine.hasLoadedTrack()) {
                    if (!engine.resume()) {
                        // Resume falhou (canal stale) — fallback recria stream do zero
                        Log.d(TAG, "togglePlayPause(): resume failed, recreating stream")
                        track?.let { engine.play(it) }
                    }
                } else if (track != null) {
                    // Canal perdido completamente — recriar do zero
                    Log.d(TAG, "togglePlayPause(): no active channel, creating fresh stream")
                    engine.play(track)
                } else {
                    Log.w(TAG, "togglePlayPause(): no track available")
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController
        if (controller != null && controllerConnected) {
            controller.seekTo(positionMs)
        } else {
            engineProvider.get().seekTo(positionMs)
        }
    }

    // ========== Conversão para MediaItem do Media3 ==========

    private fun DownloadItemEntity.toMedia3Item(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        // Adicionar artwork se disponível (mesma lógica de BassMediaSessionAdapter.toMediaItem)
        albumArtPath?.let { path ->
            try {
                val uri = if (path.startsWith("http") || path.startsWith("content://")) {
                    android.net.Uri.parse(path)
                } else {
                    android.net.Uri.fromFile(java.io.File(path))
                }
                metadataBuilder.setArtworkUri(uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set artwork URI in toMedia3Item: ${e.message}")
            }
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(outputPath)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /**
     * Chamado quando a música termina naturalmente (BASS_SYNC_END).
     * Decide o que tocar em seguida baseado no repeatMode, SEM passar pelo MediaController
     * (evita reentrância no adapter).
     */
    fun onTrackEnded() {
        if (playlist.isEmpty()) return
        val mode = uiState.value.repeatMode
        Log.d(TAG, "onTrackEnded() called, repeatMode=$mode, currentIndex=$currentIndex, playlist.size=${playlist.size}")
        when (mode) {
            1 -> {
                // Repeat ALL: avança para a próxima
                currentIndex = (currentIndex + 1) % playlist.size
                engineProvider.get().play(playlist[currentIndex])
            }
            2 -> {
                // Repeat ONE: toca a mesma de novo
                engineProvider.get().play(playlist[currentIndex])
            }
            else -> {
                // Repeat OFF: não faz nada (engine já deu stop)
                Log.d(TAG, "Repeat OFF — playback stopped at end of track")
            }
        }
    }

    // ========== Métodos de Acesso ==========

    val currentTrack: DownloadItemEntity?
        get() = _uiState.value.currentTrack

    val isPlaying: Boolean
        get() = _uiState.value.isPlaying

    val positionMs: Long
        get() = _uiState.value.positionMs

    val durationMs: Long
        get() = _uiState.value.durationMs
}
