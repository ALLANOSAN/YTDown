package com.example.ytdown.core.audio

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaybackActionDispatcher - Implementação do dispatcher de ações de reprodução.
 * Todas as fontes de comando (UI, MediaSession, Notification, Bluetooth) passam por aqui.
 */
@Singleton
class PlaybackActionDispatcherImpl @Inject constructor(
    private val controller: PlaybackController,
    private val engine: BassPlaybackEngine
) {

    companion object {
        private const val TAG = "PlaybackActionDispatcher"

        /** BASS_ATTRIB_VOL espera 0..1; fora disso o comportamento é indefinido. */
        fun coerceVolume(volume: Float): Float =
            if (volume.isNaN()) 0f else volume.coerceIn(0f, 1f)
    }

    /**
     * Volume vindo da sessão de mídia (Now Bar, Bluetooth, mute do sistema).
     *
     * Precisa passar pelo engine: `PlaybackController.updateVolume` só mexe no
     * StateFlow, e o BASS só relê esse valor quando cria um stream — mudar o
     * volume com música tocando não mudava o áudio. `engine.setVolume` aplica
     * BASS_ATTRIB_VOL no canal ativo e atualiza o estado.
     */
    fun setVolume(volume: Float) {
        val alvo = coerceVolume(volume)
        Log.d(TAG, "setVolume($volume) -> $alvo")
        engine.setVolume(alvo)
    }

    fun play() {
        Log.d(TAG, "play() called, isPlaying=${controller.isPlaying}, hasTrack=${controller.currentTrack != null}")
        // A lógica de play() original focava em verificar se precisa dar resume.
        // Agora também trata canais que morreram (ex: Bluetooth desconectou durante pausa longa).
        val track = controller.currentTrack
        if (engine.hasLoadedTrack()) {
            if (!controller.isPlaying) {
                if (!engine.resume()) {
                    // Resume falhou (canal stale após Bluetooth desconectar, etc.)
                    // Fallback: recria o stream do zero e retoma na posição salva
                    Log.d(TAG, "play(): resume failed, restarting track from scratch")
                    track?.let { engine.play(it, controller.positionMs) }
                }
            }
        } else if (track != null) {
            // Canal foi perdido completamente (dispositivo de áudio mudou, etc.)
            Log.d(TAG, "play(): no active channel, creating fresh stream for ${track.title}")
            engine.play(track)
        } else {
            Log.w(TAG, "play() called but no track loaded or available")
        }
    }

    fun resume() {
        Log.d(TAG, "resume() called, hasTrack=${controller.currentTrack != null}")
        val track = controller.currentTrack
        if (engine.hasLoadedTrack()) {
            if (!engine.resume()) {
                // Resume falhou → fallback recriando stream e retomando na posição salva
                Log.d(TAG, "resume(): failed, restarting track from scratch")
                track?.let { engine.play(it, controller.positionMs) }
            }
        } else if (track != null) {
            // Canal perdido, recriar do zero
            Log.d(TAG, "resume(): no active channel, creating fresh stream")
            engine.play(track)
        } else {
            Log.w(TAG, "resume() called but no track available")
        }
    }

    fun pause() {
        Log.d(TAG, "pause() called")
        engine.pause()
    }

    fun playPause() {
        Log.d(TAG, "playPause() called, isPlaying: ${controller.isPlaying}")
        if (controller.isPlaying) {
            pause()
        } else {
            val track = controller.currentTrack
            if (engine.hasLoadedTrack()) {
                if (!engine.resume()) {
                    // Resume falhou (canal stale após Bluetooth desconectar, etc.)
                    // Fallback: recria o stream do zero e retoma na posição salva
                    Log.d(TAG, "playPause(): resume failed, restarting track from scratch")
                    track?.let { engine.play(it, controller.positionMs) }
                }
            } else if (track != null) {
                // Canal foi perdido completamente (dispositivo de áudio mudou, etc.)
                Log.d(TAG, "playPause(): no active channel, creating fresh stream for ${track.title}")
                engine.play(track)
            } else {
                Log.w(TAG, "playPause() called but no track loaded or available")
            }
        }
    }

    fun next() {
        Log.d(TAG, "next() called")
        controller.playNext()
    }

    fun previous() {
        Log.d(TAG, "previous() called")
        controller.playPrevious()
    }

    fun rewind() {
        Log.d(TAG, "rewind() called")
        val current = controller.positionMs
        engine.seekTo((current - 10000).coerceAtLeast(0))
    }

    fun forward() {
        Log.d(TAG, "forward() called")
        val current = controller.positionMs
        val duration = controller.durationMs
        engine.seekTo((current + 10000).coerceAtMost(duration))
    }

    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo($positionMs) called")
        engine.seekTo(positionMs)
    }

    fun toggleShuffle() {
        Log.d(TAG, "toggleShuffle() called")
        val newShuffle = !controller.uiState.value.isShuffleEnabled
        controller.updateShuffle(newShuffle)
    }

    fun toggleRepeatMode() {
        Log.d(TAG, "toggleRepeatMode() called")
        val nextMode = (controller.uiState.value.repeatMode + 1) % 3
        controller.updateRepeatMode(nextMode)
    }
}
