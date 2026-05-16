package com.example.ytdown.core.audio

import com.un4seen.bass.BASS
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BassFXEngine - Gerencia o pipeline DSP e efeitos usando BASS_FX.
 * Implementa Equalizador Paramétrico e efeitos de Tempo/Pitch.
 */
@Singleton
class BassFXEngine @Inject constructor(
    private val playbackEngine: BassPlaybackEngine
) {
    private val TAG = "BassFXEngine"
    
    // Equalizador (10 bandas padrão)
    private val eqBands = IntArray(10)
    private val eqGains = FloatArray(10) { 0f }
    private val frequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    /**
     * Aplica o Equalizador ao canal ativo.
     */
    fun setupEqualizer() {
        val channel = playbackEngine.getActiveChannel()
        if (channel == 0) return

        for (i in 0 until 10) {
            // Remove o efeito anterior antes de criar um novo para evitar vazamentos
            if (eqBands[i] != 0) {
                BASS.BASS_ChannelRemoveFX(channel, eqBands[i])
                eqBands[i] = 0
            }
            
            // BASS_FX_DX8_PARAMEQ é embutido no BASS (não precisa do plugin BASS_FX para EQ básico DX8)
            eqBands[i] = BASS.BASS_ChannelSetFX(channel, BASS.BASS_FX_DX8_PARAMEQ, 0)
            
            // Verifica se alocou com sucesso antes de aplicar parâmetros
            if (eqBands[i] != 0) {
                val params = BASS.BASS_DX8_PARAMEQ()
                params.fCenter = frequencies[i]
                params.fBandwidth = 18f
                params.fGain = eqGains[i]
                BASS.BASS_FXSetParameters(eqBands[i], params)
            }
        }
    }

    /**
     * Ajusta o ganho de uma banda específica (-15dB a +15dB).
     */
    fun setBandGain(bandIndex: Int, gain: Float) {
        if (bandIndex !in 0 until 10) return
        
        eqGains[bandIndex] = gain.coerceIn(-15f, 15f)
        
        val handle = eqBands[bandIndex]
        if (handle != 0) {
            val params = BASS.BASS_DX8_PARAMEQ()
            if (BASS.BASS_FXGetParameters(handle, params)) {
                params.fGain = eqGains[bandIndex]
                BASS.BASS_FXSetParameters(handle, params)
            }
        }
    }

    fun getBandGain(bandIndex: Int): Float = eqGains.getOrElse(bandIndex) { 0f }

    /**
     * Aplica Reverb ao canal ativo.
     */
    fun setReverb(mix: Float) {
        val channel = playbackEngine.getActiveChannel()
        if (channel == 0) return

        val rvHandle = BASS.BASS_ChannelSetFX(channel, BASS.BASS_FX_DX8_REVERB, 1)
        if (rvHandle != 0) {
            val params = BASS.BASS_DX8_REVERB()
            params.fInGain = 0f
            params.fReverbMix = mix.coerceIn(-96f, 0f)
            BASS.BASS_FXSetParameters(rvHandle, params)
        }
    }
}
