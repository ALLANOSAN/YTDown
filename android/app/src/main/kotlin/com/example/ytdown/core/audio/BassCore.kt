package com.example.ytdown.core.audio

import android.content.Context
import android.util.Log
import com.un4seen.bass.BASS

/**
 * BassCore - Gerenciamento centralizado do motor nativo BASS.
 * Segue o padrão Singleton para garantir uma única instância do engine.
 */
object BassCore {
    private const val TAG = "BassCore"
    private var isInitialized = false

    /**
     * Inicializa o motor BASS com configurações profissionais.
     * @param context Contexto da aplicação.
     * @param frequency Taxa de amostragem (padrão 44100Hz).
     */
    fun initialize(context: Context, frequency: Int = 44100) {
        if (isInitialized) return

        Log.i(TAG, "Iniciando BASS Core Engine...")

        // 1. Configurar BASS antes da inicialização se necessário
        // BASS_CONFIG_DEV_NONSTOP: Mantém o dispositivo de áudio ativo mesmo sem canais tocando (evita clicks ao iniciar)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_DEV_NONSTOP, 1)
        
        // Habilitar AAudio para menor latência (Android 8+)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_ANDROID_AAUDIO, 1)

        // 2. Inicializar o dispositivo padrão (-1)
        // Usamos BASS_DEVICE_OPENSLES como fallback/padrão para melhor performance.
        if (!BASS.BASS_Init(-1, frequency, BASS.BASS_DEVICE_OPENSLES)) {
            val error = BASS.BASS_ErrorGetCode()
            Log.e(TAG, "Falha ao inicializar BASS: ${BassErrorMapper.getErrorMessage(error)}")
            return
        }

        // 3. Carregar Plugins (Placeholder para futuras extensões, ex: AAC/FLAC)
        // BASS.BASS_PluginLoad("libbass_aac.so", 0)

        // 4. Configurações Globais Pós-Init
        
        // Habilitar processamento Float DSP globalmente para máxima fidelidade
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_FLOATDSP, 1)
        
        // Aumentar o buffer de atualização para 100ms (padrão é ~10ms em alguns sistemas)
        // Isso ajuda na estabilidade do processamento DSP pesado
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_UPDATEPERIOD, 100)
        
        // Configurar buffer de rede para streaming (5 segundos)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_NET_BUFFER, 5000)

        // 5. Obter informações do dispositivo para validação
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
     * Verifica se o motor está inicializado.
     */
    fun isReady(): Boolean = isInitialized
}
