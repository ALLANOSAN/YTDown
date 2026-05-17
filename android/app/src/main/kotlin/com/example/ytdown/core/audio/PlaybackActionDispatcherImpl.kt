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
        Log.d(TAG, "play() called")
        // A lógica de play() original focava em verificar se precisa dar resume.
        // Vamos manter a lógica básica para evitar quebra.
        if (engine.hasLoadedTrack()) {
            if (!controller.isPlaying) {
                engine.resume()
            }
        }
    }

    override fun resume() {
        Log.d(TAG, "resume() called")
        if (engine.hasLoadedTrack()) {
            engine.resume()
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
        // Próximamente: implementação com playlist
    }

    override fun previous() {
        Log.d(TAG, "previous() called")
        // Próximamente: implementação com playlist
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
