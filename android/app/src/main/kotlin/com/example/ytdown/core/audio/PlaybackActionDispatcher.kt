package com.example.ytdown.core.audio

/**
 * PlaybackActionDispatcher - Interface unificada para todos os comandos de reprodução.
 * Componentes da UI e do sistema (MediaSession, Notificações, etc.) devem usar esta interface.
 */
interface PlaybackActionDispatcher {
    fun play()
    fun resume()
    fun pause()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun toggleRepeatMode()
}
