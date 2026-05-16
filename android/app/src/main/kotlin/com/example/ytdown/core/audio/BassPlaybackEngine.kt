package com.example.ytdown.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ytdown.core.domain.DownloadItemEntity
import com.un4seen.bass.BASS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BassPlaybackEngine - O motor de reprodução profissional baseado em BASS.
 * Gerencia o ciclo de vida dos canais, streams e sincronização de eventos.
 */
@Singleton
class BassPlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: PlaybackStateManager,
    private val fxEngineProvider: dagger.Lazy<BassFXEngine>
) {
    private val TAG = "BassPlaybackEngine"
    
    private val fxEngine get() = fxEngineProvider.get()
    
    private var activeChannel = 0 // Canal principal de áudio
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    // Callbacks Natividade
    private val endSync = BASS.SYNCPROC { handle, channel, data, user ->
        Log.d(TAG, "Fim da reprodução detectado no canal $channel")
        engineScope.launch {
            stop()
            // Notificar fim para progressão da fila (via stateManager ou callback)
            // nextTrack()
        }
    }

    /**
     * Prepara e inicia a reprodução de um item.
     */
    fun play(item: DownloadItemEntity) {
        val path = item.exportedPath ?: item.outputPath
        if (path.isBlank()) {
            stateManager.setError("Caminho de arquivo inválido")
            return
        }

        stop() // Garante que o canal anterior seja limpo

        // 1. Criar o stream (Local, URL ou SAF)
        val channel = createStream(path)
        
        if (channel == 0) {
            val error = BASS.BASS_ErrorGetCode()
            val msg = BassErrorMapper.getErrorMessage(error)
            Log.e(TAG, "Erro ao criar stream: $msg")
            stateManager.setError(msg)
            return
        }

        activeChannel = channel

        // 2. Configurar Sincronização
        BASS.BASS_ChannelSetSync(activeChannel, BASS.BASS_SYNC_END, 0, endSync, 0)

        // 3. Aplicar atributos iniciais e DSP
        BASS.BASS_ChannelSetAttribute(activeChannel, BASS.BASS_ATTRIB_VOL, stateManager.uiState.value.volume)
        fxEngine.setupEqualizer()

        // 4. Iniciar Playback
        if (BASS.BASS_ChannelPlay(activeChannel, false)) {
            stateManager.updateTrack(item)
            stateManager.updatePlaying(true)
            updateDuration()
            startProgressTracker()
        } else {
            val error = BASS.BASS_ErrorGetCode()
            stateManager.setError(BassErrorMapper.getErrorMessage(error))
        }
    }

    private fun createStream(path: String): Int {
        return when {
            path.startsWith("content://") -> {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
                    if (pfd != null) {
                        // Importante: BASS_SAMPLE_FLOAT para processamento 32-bit
                        val handle = BASS.BASS_StreamCreateFile(pfd, 0, pfd.statSize, BASS.BASS_SAMPLE_FLOAT)
                        // BASS cria uma cópia interna do descritor de arquivo; podemos fechar o original seguramente.
                        pfd.close()
                        handle
                    } else 0
                } catch (e: Exception) {
                    Log.e(TAG, "Erro SAF: ${e.message}")
                    0
                }
            }
            path.startsWith("http") -> {
                BASS.BASS_StreamCreateURL(path, 0, BASS.BASS_SAMPLE_FLOAT, null, null)
            }
            else -> {
                BASS.BASS_StreamCreateFile(path, 0, 0, BASS.BASS_SAMPLE_FLOAT)
            }
        }
    }

    fun pause() {
        if (activeChannel != 0 && BASS.BASS_ChannelPause(activeChannel)) {
            stateManager.updatePlaying(false)
            stopProgressTracker()
        }
    }

    /**
     * Realiza uma transição suave (Crossfade) entre a música atual e a próxima.
     * @param nextChannel O handle do canal da nova música que já deve estar criado.
     * @param durationMs Duração da transição em milissegundos.
     */
    fun performCrossfade(nextChannel: Int, durationMs: Int = 2000) {
        if (activeChannel != 0) {
            // Fade out da música atual
            BASS.BASS_ChannelSlideAttribute(activeChannel, BASS.BASS_ATTRIB_VOL, 0f, durationMs)
            
            // Inicia a nova música com volume 0
            BASS.BASS_ChannelSetAttribute(nextChannel, BASS.BASS_ATTRIB_VOL, 0f)
            BASS.BASS_ChannelPlay(nextChannel, false)
            
            // Fade in da nova música
            BASS.BASS_ChannelSlideAttribute(nextChannel, BASS.BASS_ATTRIB_VOL, stateManager.uiState.value.volume, durationMs)
            
            // Agenda a parada e liberação do canal antigo após o fade out
            engineScope.launch {
                delay(durationMs.toLong())
                stop() // Isso liberará o canal antigo após o crossfade
                activeChannel = nextChannel
            }
        } else {
            // Caso não haja música tocando, apenas inicia a nova
            activeChannel = nextChannel
            BASS.BASS_ChannelPlay(activeChannel, false)
        }
    }

    fun stop() {
        if (activeChannel != 0) {
            BASS.BASS_ChannelStop(activeChannel)
            BASS.BASS_StreamFree(activeChannel)
            activeChannel = 0
        }
        stateManager.updatePlaying(false)
        stateManager.updatePosition(0L)
        stopProgressTracker()
    }

    fun seekTo(posMs: Long) {
        if (activeChannel != 0) {
            val seconds = posMs / 1000.0
            val bytes = BASS.BASS_ChannelSeconds2Bytes(activeChannel, seconds)
            if (BASS.BASS_ChannelSetPosition(activeChannel, bytes, BASS.BASS_POS_BYTE)) {
                stateManager.updatePosition(posMs)
            }
        }
    }

    private fun updateDuration() {
        if (activeChannel != 0) {
            val bytes = BASS.BASS_ChannelGetLength(activeChannel, BASS.BASS_POS_BYTE)
            val seconds = BASS.BASS_ChannelBytes2Seconds(activeChannel, bytes)
            stateManager.updateDuration((seconds * 1000).toLong())
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = engineScope.launch {
            while (isActive) {
                if (activeChannel != 0) {
                    val bytes = BASS.BASS_ChannelGetPosition(activeChannel, BASS.BASS_POS_BYTE)
                    val seconds = BASS.BASS_ChannelBytes2Seconds(activeChannel, bytes)
                    stateManager.updatePosition((seconds * 1000).toLong())
                }
                delay(500) // Atualização a cada 500ms
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * Extração de dados FFT para o visualizador (1024 floats).
     */
    fun getFftData(buffer: ByteBuffer) {
        if (activeChannel != 0) {
            BASS.BASS_ChannelGetData(activeChannel, buffer, BASS.BASS_DATA_FFT2048)
        }
    }

    fun setVolume(volume: Float) {
        if (activeChannel != 0) {
            BASS.BASS_ChannelSetAttribute(activeChannel, BASS.BASS_ATTRIB_VOL, volume)
        }
        stateManager.updateVolume(volume)
    }

    fun getActiveChannel(): Int = activeChannel
}
