package com.example.ytdown.core.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regras de foco de audio, separadas do framework para poderem ser testadas.
 *
 * O app nunca pedia foco (requestAudioFocus nao existia no projeto), entao abrir
 * o YouTube nao pausava a reproducao. Com ExoPlayer isso vem de graca via
 * setAudioAttributes(handleAudioFocus = true); como o player e o BASS por tras de
 * um SimpleBasePlayer, o Media3 nao gerencia foco e a politica e nossa.
 */
class AudioFocusPolicyTest {

    @Test
    fun `perda definitiva pausa e nao retoma`() {
        assertEquals(
            AudioFocusAction.PAUSE,
            AudioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS, wasPlaying = true),
        )
    }

    @Test
    fun `perda transitoria pausa`() {
        assertEquals(
            AudioFocusAction.PAUSE,
            AudioFocusPolicy.onFocusChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, wasPlaying = true
            ),
        )
    }

    @Test
    fun `perda transitoria com duck abaixa o volume em vez de pausar`() {
        assertEquals(
            AudioFocusAction.DUCK,
            AudioFocusPolicy.onFocusChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, wasPlaying = true
            ),
        )
    }

    @Test
    fun `ganho retoma quando estava tocando antes de perder o foco`() {
        assertEquals(
            AudioFocusAction.RESUME,
            AudioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = true),
        )
    }

    @Test
    fun `ganho nao retoma se o usuario ja tinha pausado por conta propria`() {
        assertEquals(
            AudioFocusAction.RESTORE_VOLUME,
            AudioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false),
        )
    }

    @Test
    fun `codigo desconhecido nao faz nada`() {
        assertEquals(
            AudioFocusAction.NONE,
            AudioFocusPolicy.onFocusChange(9999, wasPlaying = true),
        )
    }
}
