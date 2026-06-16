package com.example.ytdown.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ytdown.core.domain.DownloadItemEntity
import com.un4seen.bass.BASS
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Lazy
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BassPlaybackEngine - O motor de reprodução profissional baseado em BASS.
 * Gerencia o ciclo de vida dos canais, streams e sincronização de eventos.
 * 
 * IMPORTANTE: Atualiza o PlaybackController (Single Source of Truth) diretamente.
 */
@Singleton
class BassPlaybackEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controllerProvider: Lazy<PlaybackController>,
    private val fxEngineProvider: Lazy<BassFXEngine>
) {
    init {
        android.util.Log.e("BassPlaybackEngine", "ENGINE CREATED")
    }

    // Propriedade lazy para evitar dependência circular na inicialização
    private val controller: PlaybackController by lazy { controllerProvider.get() }
    private val fxEngine: BassFXEngine by lazy { fxEngineProvider.get() }
    
    companion object {
        private const val TAG = "BassPlaybackEngine"
    }
    
    private var activeChannel = 0 // Canal principal de áudio
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    // Callbacks Natividade
    private val endSync = BASS.SYNCPROC { handle, channel, data, user ->
        Log.d(TAG, "Fim da reprodução detectado no canal $channel")
        engineScope.launch {
            stop()
            controller.onTrackEnded()
        }
    }

    /**
     * Prepara e inicia a reprodução de um item.
     */
    /**
     * Inicia a reprodução de uma música (novo stream ou recriação após resume falho).
     * @param item A música a ser tocada.
     * @param resumePositionMs Posição em ms para seek após criar o stream (> 0 = retomada).
     *                         Usado quando resume() falha (PAUSED_DEVICE/BASS_ERROR_START)
     *                         e precisamos recriar o stream mantendo a posição original.
     */
    fun play(item: DownloadItemEntity, resumePositionMs: Long = -1L) {
        Log.d(TAG, "play() called for: ${item.title}, resumePos: ${resumePositionMs}ms")
        val path = item.exportedPath ?: item.outputPath
        if (path.isBlank()) {
            controller.setError("Caminho de arquivo inválido")
            return
        }

        stop() // Garante que o canal anterior seja limpo

        // 1. Criar o stream (Local, URL ou SAF)
        var channel = createStream(path)

        // 1a. Se falhou com erro de dispositivo, tenta reinicializar BASS e retentar
        if (channel == 0) {
            val error = BASS.BASS_ErrorGetCode()
            if (BassCore.isDeviceRelatedError(error)) {
                Log.w(TAG, "Device-related error ($error) ao criar stream, tentando reinicializar BASS...")
                if (BassCore.reinitialize()) {
                    channel = createStream(path)
                    if (channel != 0) {
                        Log.d(TAG, "Stream criado com sucesso após reinit do BASS")
                    }
                }
            }
        }

        if (channel == 0) {
            val error = BASS.BASS_ErrorGetCode()
            val msg = BassErrorMapper.getErrorMessage(error)
            Log.e(TAG, "Erro ao criar stream: $msg")
            controller.setError(msg)
            return
        }

        activeChannel = channel

        // 2. Configurar Sincronização
        BASS.BASS_ChannelSetSync(activeChannel, BASS.BASS_SYNC_END, 0, endSync, 0)

        // 3. Aplicar atributos iniciais e DSP
        BASS.BASS_ChannelSetAttribute(activeChannel, BASS.BASS_ATTRIB_VOL, controller.uiState.value.volume)
        fxEngine.setupEqualizer()

        // 4. Iniciar Playback
        if (BASS.BASS_ChannelPlay(activeChannel, false)) {
            // Retomar na posição salva (fallback após resume() falhar)
            if (resumePositionMs > 0) {
                seekTo(resumePositionMs)
            }
            controller.updateTrack(item)
            controller.updatePlaying(true)
            updateDuration()
            startProgressTracker()
            Log.d(TAG, "Playback started successfully")
        } else {
            val error = BASS.BASS_ErrorGetCode()
            BASS.BASS_StreamFree(activeChannel)
            activeChannel = 0

            if (BassCore.isDeviceRelatedError(error)) {
                Log.w(TAG, "BASS_ChannelPlay device error ($error) — reinitializing BASS...")
                if (BassCore.reinitialize()) {
                    val retryChannel = createStream(path)
                    if (retryChannel != 0) {
                        activeChannel = retryChannel
                        BASS.BASS_ChannelSetSync(activeChannel, BASS.BASS_SYNC_END, 0, endSync, 0)
                        BASS.BASS_ChannelSetAttribute(activeChannel, BASS.BASS_ATTRIB_VOL, controller.uiState.value.volume)
                        fxEngine.setupEqualizer()
                        if (BASS.BASS_ChannelPlay(activeChannel, false)) {
                            // Retomar na posição salva também no retry
                            if (resumePositionMs > 0) {
                                seekTo(resumePositionMs)
                            }
                            controller.updateTrack(item)
                            controller.updatePlaying(true)
                            updateDuration()
                            startProgressTracker()
                            Log.d(TAG, "Playback started after BASS reinit")
                            return
                        }
                        BASS.BASS_StreamFree(activeChannel)
                        activeChannel = 0
                    }
                }
            }

            val msg = BassErrorMapper.getErrorMessage(error)
            Log.e(TAG, "Playback failed definitively: $msg")
            controller.setError(msg)
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
        Log.d(TAG, "pause() called, activeChannel: $activeChannel")
        if (activeChannel != 0 && BASS.BASS_ChannelPause(activeChannel)) {
            controller.updatePlaying(false)
            controller.updateBuffering(false)
            stopProgressTracker()
            Log.d(TAG, "Pause successful")
        } else {
            Log.w(TAG, "Pause failed, channel may be inactive")
        }
    }

    /**
     * Retoma a reprodução do canal ativo (quando há uma música carregada e pausada).
     * Este método é usado pelo dispatcher quando o usuário quer "continuar" a reprodução.
     */
    /**
     * Retoma a reprodução do canal ativo.
     * @return true se o resume foi bem-sucedido, false se falhou (canal inválido/morto).
     */
    fun resume(): Boolean {
        Log.d(TAG, "resume() called, activeChannel: $activeChannel")
        if (activeChannel != 0) {
            val isActive = BASS.BASS_ChannelIsActive(activeChannel)
            if (isActive == BASS.BASS_ACTIVE_STOPPED || isActive == BASS.BASS_ACTIVE_PAUSED) {
                if (BASS.BASS_ChannelPlay(activeChannel, false)) {
                    controller.updatePlaying(true)
                    controller.updateBuffering(false)
                    startProgressTracker()
                    Log.d(TAG, "Resume successful")
                    return true
                } else {
                    val error = BASS.BASS_ErrorGetCode()
                    Log.e(TAG, "Resume failed, error: $error")
                    // Não setar erro aqui — o caller fará o fallback recriando o stream
                    return false
                }
            } else if (isActive == BASS.BASS_ACTIVE_PAUSED_DEVICE) {
                // Dispositivo de áudio mudou/dormiu enquanto o canal estava pausado
                // (ex: Bluetooth A2DP suspenso). BASS_Free + BASS_Init força o BT a acordar
                // antes do fallback recriar o stream, evitando que o ChannelPlay seguinte
                // "toque" sem áudio (0→3→reset) e dispare endSync prematuro.
                Log.w(TAG, "Resume: PAUSED_DEVICE — reinicializando BASS para forçar wake-up do BT A2DP")
                BassCore.reinitialize()
                activeChannel = 0  // handle inválido após BASS_Free
                return false
            } else if (isActive == BASS.BASS_ACTIVE_PLAYING) {
                Log.d(TAG, "Already playing")
                return true
            } else {
                Log.w(TAG, "Resume: channel in unexpected state $isActive")
                return false
            }
        } else {
            Log.w(TAG, "Resume called but no active channel")
            return false
        }
    }

    /**
     * Retorna true se há uma música carregada e pronta para reprodução.
     */
    fun hasLoadedTrack(): Boolean = activeChannel != 0 && controller.currentTrack != null

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
            BASS.BASS_ChannelSlideAttribute(nextChannel, BASS.BASS_ATTRIB_VOL, controller.uiState.value.volume, durationMs)
            
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
        Log.d(TAG, "stop() called, activeChannel: $activeChannel")
        if (activeChannel != 0) {
            BASS.BASS_ChannelStop(activeChannel)
            BASS.BASS_StreamFree(activeChannel)
            activeChannel = 0
        }
        controller.updatePlaying(false)
        controller.updatePosition(0L)
        stopProgressTracker()
        Log.d(TAG, "Stop complete")
    }

    fun seekTo(posMs: Long) {
        if (activeChannel != 0) {
            val seconds = posMs / 1000.0
            val bytes = BASS.BASS_ChannelSeconds2Bytes(activeChannel, seconds)
            if (BASS.BASS_ChannelSetPosition(activeChannel, bytes, BASS.BASS_POS_BYTE)) {
                controller.updatePosition(posMs)
                Log.d(TAG, "Seek to $posMs ms successful")
            }
        }
    }

    private fun updateDuration() {
        if (activeChannel != 0) {
            val bytes = BASS.BASS_ChannelGetLength(activeChannel, BASS.BASS_POS_BYTE)
            val seconds = BASS.BASS_ChannelBytes2Seconds(activeChannel, bytes)
            controller.updateDuration((seconds * 1000).toLong())
            Log.d(TAG, "Duration updated: ${(seconds * 1000).toLong()} ms")
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = engineScope.launch {
            val fftBuffer = java.nio.ByteBuffer.allocateDirect(1024)
            val fftArray = FloatArray(64)
            while (isActive) {
                if (activeChannel != 0) {
                    val bytes = BASS.BASS_ChannelGetPosition(activeChannel, BASS.BASS_POS_BYTE)
                    val status = BASS.BASS_ChannelIsActive(activeChannel)
                    
                    controller.updateBuffering(status == BASS.BASS_ACTIVE_STALLED)
                    
                    val seconds = BASS.BASS_ChannelBytes2Seconds(activeChannel, bytes)
                    controller.updatePosition((seconds * 1000).toLong())

                    // Coleta de dados FFT para o visualizador (64 bandas)
                    BASS.BASS_ChannelGetData(activeChannel, fftBuffer, BASS.BASS_DATA_FFT512)
                    fftBuffer.asFloatBuffer().get(fftArray)
                    controller.updateSpectrum(fftArray.copyOf())
                    fftBuffer.clear()
                }
                delay(50) // 20 FPS para visualizador fluido
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
        controller.updateVolume(volume)
    }

    fun getActiveChannel(): Int = activeChannel
}
