package com.example.ytdown.core.audio

import android.content.Context
import android.util.Log
import com.un4seen.bass.BASS
import com.example.ytdown.utils.LocalLogger

/**
 * BassCore - Gerenciamento centralizado do motor nativo BASS.
 * Segue o padrão Singleton para garantir uma única instância do engine.
 */
object BassCore {
    private const val TAG = "BassCore"
    private var isInitialized = false
    private var lastContext: Context? = null
    private var lastFrequency: Int = 44100

    /**
     * Inicializa o motor BASS com configurações profissionais.
     * @param context Contexto da aplicação.
     * @param frequency Taxa de amostragem (padrão 44100Hz).
     */
    fun initialize(context: Context, frequency: Int = 44100) {
        if (isInitialized) return

        Log.i(TAG, "Iniciando BASS Core Engine...")
        lastContext = context
        lastFrequency = frequency

        // 1. Configurar BASS antes da inicialização se necessário
        // BASS_CONFIG_DEV_NONSTOP: Mantém o dispositivo de áudio ativo mesmo sem canais tocando (evita clicks ao iniciar)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_DEV_NONSTOP, 1)
        
        // Habilitar AAudio para menor latência (Android 8+)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_ANDROID_AAUDIO, 1)

        // 2. Inicializar o dispositivo padrão (-1)
        // Usamos BASS_DEVICE_OPENSLES como fallback/padrão para melhor performance.
        try {
            val success = BASS.BASS_Init(-1, frequency, BASS.BASS_DEVICE_OPENSLES)
            LocalLogger.debug("RESULT: $success", tag = "BASS_INIT")
            if (!success) {
                val error = BASS.BASS_ErrorGetCode()
                LocalLogger.error("Falha ao inicializar BASS: ${BassErrorMapper.getErrorMessage(error)}", tag = TAG)
                return
            }
        } catch (e: Exception) {
            LocalLogger.error("FAILED", e, "BASS_INIT")
            return
        }

        // 3. Iniciar output global de áudio
        // Necessário após BASS_Init para garantir que o mixer esteja rodando;
        // em alguns dispositivos/Bluetooth o Android suspende o output durante
        // pausa longa, causando BASS_ERROR_START no ChannelPlay seguinte.
        BASS.BASS_Start()

        // 4. Carregar Plugins (Placeholder para futuras extensões, ex: AAC/FLAC)
        // BASS.BASS_PluginLoad("libbass_aac.so", 0)

        // 5. Configurações Globais Pós-Init
        
        // Habilitar processamento Float DSP globalmente para máxima fidelidade
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_FLOATDSP, 1)
        
        // Aumentar o buffer de atualização para 100ms (padrão é ~10ms em alguns sistemas)
        // Isso ajuda na estabilidade do processamento DSP pesado
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_UPDATEPERIOD, 100)
        
        // Configurar buffer de rede para streaming (5 segundos)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_BUFFER, 5000)

        // 6. Obter informações do dispositivo para validação
        val info = BASS.BASS_INFO()
        if (BASS.BASS_GetInfo(info)) {
            Log.i(TAG, "BASS Inicializado com Sucesso!")
            Log.d(TAG, "Dispositivo Flags: ${info.flags}")
            Log.d(TAG, "Latência: ${info.latency}ms")
            Log.d(TAG, "Sample Rate Nativa: ${info.freq}Hz")
        }

        isInitialized = true
    }

    /**
     * Libera todos os recursos do motor BASS.
     * CRUCIAL: Deve ser chamado no onDestroy da sua Activity/Service principal
     * para evitar vazamentos de memória e falhas no dispositivo.
     */
    fun release() {
        if (!isInitialized) return
        Log.i(TAG, "Liberando BASS Core Engine...")
        BASS.BASS_Free()
        isInitialized = false
    }

    /**
     * Reinicializa o motor BASS após detecção de erro de dispositivo.
     * Chamado quando BASS_ERROR_DRIVER, BASS_ERROR_INIT, BASS_ERROR_REINIT
     * ou BASS_ERROR_START são detectados
     * (ex: Bluetooth A2DP suspendeu output durante pausa longa).
     * @return true se a reinicialização foi bem-sucedida.
     */
    fun reinitialize(): Boolean {
        Log.i(TAG, "Reinicializando BASS Core Engine após falha de dispositivo...")
        val ctx = lastContext ?: return false
        release()  // BASS_Free + isInitialized = false
        initialize(ctx, lastFrequency)
        return isReady()
    }

    /**
     * Verifica se o código de erro BASS está relacionado a dispositivo de áudio.
     * Estes erros indicam que o output de áudio mudou (Bluetooth des/reconectou)
     * e o BASS precisa ser reinicializado.
     */
    fun isDeviceRelatedError(errorCode: Int): Boolean {
        return errorCode == BASS.BASS_ERROR_DRIVER ||
                errorCode == BASS.BASS_ERROR_INIT ||
                errorCode == BASS.BASS_ERROR_HANDLE ||
                errorCode == BASS.BASS_ERROR_REINIT ||
                errorCode == BASS.BASS_ERROR_START
    }

    /**
     * Verifica se o motor está inicializado.
     */
    fun isReady(): Boolean = isInitialized
}
