package com.example.ytdown.core.audio

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaybackActionDispatcherImpl - Implementação do dispatcher de ações de reprodução.
 * Todas as fontes de comando (UI, MediaSession, Notification, Bluetooth) passam por aqui.
 */
@Singleton
class PlaybackActionDispatcherImpl @Inject constructor(
    private val controller: PlaybackController,
    private val engine: BassPlaybackEngine
) : PlaybackActionDispatcher {

    companion object {
        private const val TAG = "PlaybackActionDispatcher"
    }

    override fun play() {
        Log.d(TAG, "play() called, isPlaying=${controller.isPlaying}, hasTrack=${controller.currentTrack != null}")
        // A lógica de play() original focava em verificar se precisa dar resume.
        // Agora também trata canais que morreram (ex: Bluetooth desconectou durante pausa longa).
        val track = controller.currentTrack
        if (engine.hasLoadedTrack()) {
            if (!controller.isPlaying) {
                if (!engine.resume()) {
                    // Resume falhou (canal stale após Bluetooth desconectar, etc.)
                    // Fallback: recria o stream do zero no dispositivo de áudio atual
                    Log.d(TAG, "play(): resume failed, restarting track from scratch")
                    track?.let { engine.play(it) }
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

    override fun resume() {
        Log.d(TAG, "resume() called, hasTrack=${controller.currentTrack != null}")
        val track = controller.currentTrack
        if (engine.hasLoadedTrack()) {
            if (!engine.resume()) {
                // Resume falhou → fallback recriando stream
                Log.d(TAG, "resume(): failed, restarting track from scratch")
                track?.let { engine.play(it) }
            }
        } else if (track != null) {
            // Canal perdido, recriar do zero
            Log.d(TAG, "resume(): no active channel, creating fresh stream")
            engine.play(track)
        } else {
            Log.w(TAG, "resume() called but no track available")
        }
    }

    override fun pause() {
        Log.d(TAG, "pause() called")
        engine.pause()
    }

    override fun playPause() {
        Log.d(TAG, "playPause() called, isPlaying: ${controller.isPlaying}")
        if (controller.isPlaying) {
            pause()
        } else {
            // Se há um canal carregado mas está pausado, usa resume()
            if (engine.hasLoadedTrack()) {
                engine.resume()
            } else {
                Log.w(TAG, "playPause() called but no track loaded")
            }
        }
    }

    override fun next() {
        Log.d(TAG, "next() called")
        controller.playNext()
    }

    override fun previous() {
        Log.d(TAG, "previous() called")
        controller.playPrevious()
    }

    override fun rewind() {
        Log.d(TAG, "rewind() called")
        val current = controller.positionMs
        engine.seekTo((current - 10000).coerceAtLeast(0))
    }

    override fun forward() {
        Log.d(TAG, "forward() called")
        val current = controller.positionMs
        val duration = controller.durationMs
        engine.seekTo((current + 10000).coerceAtMost(duration))
    }

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo($positionMs) called")
        engine.seekTo(positionMs)
    }

    override fun toggleShuffle() {
        Log.d(TAG, "toggleShuffle() called")
        val newShuffle = !controller.uiState.value.isShuffleEnabled
        controller.updateShuffle(newShuffle)
    }

    override fun toggleRepeatMode() {
        Log.d(TAG, "toggleRepeatMode() called")
        val nextMode = (controller.uiState.value.repeatMode + 1) % 3
        controller.updateRepeatMode(nextMode)
    }
}
