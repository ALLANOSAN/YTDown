package com.example.ytdown.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pede e devolve o foco de audio ao sistema.
 *
 * Sem isto o app nao era avisado quando outro app comecava a tocar, entao o BASS
 * continuava rodando por cima do YouTube. O ExoPlayer faz isso sozinho via
 * setAudioAttributes(handleAudioFocus = true); com um player customizado sobre o
 * SimpleBasePlayer, o Media3 nao gerencia foco e a responsabilidade e nossa.
 *
 * A decisao de o que fazer com cada codigo fica em [AudioFocusPolicy], que e
 * testavel sem framework. Aqui so mora a conversa com o AudioManager.
 */
@Singleton
class AudioFocusManager
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private companion object {
        const val TAG = "AudioFocusManager"
        const val DUCK_VOLUME = 0.2f
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    /** Preenchido por quem usa; mantido aqui para o listener nao depender do engine. */
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onVolume: ((Float) -> Unit)? = null
    var isPlaying: (() -> Boolean)? = null

    private var wasPlayingBeforeLoss = false
    private var volumeBeforeDuck = 1.0f

    private val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val acao = AudioFocusPolicy.onFocusChange(focusChange, wasPlayingBeforeLoss)
        Log.d(TAG, "foco mudou: $focusChange -> $acao (tocava antes: $wasPlayingBeforeLoss)")

        when (acao) {
            AudioFocusAction.PAUSE -> {
                wasPlayingBeforeLoss = isPlaying?.invoke() ?: false
                onPause?.invoke()
            }
            AudioFocusAction.DUCK -> {
                wasPlayingBeforeLoss = isPlaying?.invoke() ?: false
                onVolume?.invoke(DUCK_VOLUME)
            }
            AudioFocusAction.RESUME -> {
                onVolume?.invoke(volumeBeforeDuck)
                onResume?.invoke()
                wasPlayingBeforeLoss = false
            }
            AudioFocusAction.RESTORE_VOLUME -> onVolume?.invoke(volumeBeforeDuck)
            AudioFocusAction.NONE -> Unit
        }
    }

    /** Pede o foco. Retorna false se o sistema negar — nesse caso nao se deve tocar. */
    fun request(currentVolume: Float): Boolean {
        if (hasFocus) return true
        volumeBeforeDuck = currentVolume

        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val resultado = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pedido = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(atributos)
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = pedido
            audioManager.requestAudioFocus(pedido)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasFocus = resultado == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, if (hasFocus) "foco concedido" else "foco negado ($resultado)")
        return hasFocus
    }

    /** Devolve o foco. Chamar ao parar de vez, nao ao pausar. */
    fun abandon() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        focusRequest = null
        hasFocus = false
        wasPlayingBeforeLoss = false
    }
}
