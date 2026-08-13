package com.example.ytdown.core.audio

import android.media.AudioManager

/** O que fazer quando o foco de audio muda. */
enum class AudioFocusAction {
    PAUSE,
    RESUME,
    DUCK,
    RESTORE_VOLUME,
    NONE,
}

/**
 * Decide a acao a partir do codigo de foco. Sem dependencia de Context ou de
 * estado mutavel, entao roda em teste JVM puro.
 */
object AudioFocusPolicy {

    /**
     * @param focusChange codigo vindo do OnAudioFocusChangeListener.
     * @param wasPlaying se a reproducao estava ativa quando o foco foi perdido.
     *        Sem isso, voltar do YouTube daria play numa musica que o usuario
     *        tinha pausado de proposito.
     */
    fun onFocusChange(focusChange: Int, wasPlaying: Boolean): AudioFocusAction = when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusAction.PAUSE

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusAction.DUCK

        AudioManager.AUDIOFOCUS_GAIN ->
            if (wasPlaying) AudioFocusAction.RESUME else AudioFocusAction.RESTORE_VOLUME

        else -> AudioFocusAction.NONE
    }
}
