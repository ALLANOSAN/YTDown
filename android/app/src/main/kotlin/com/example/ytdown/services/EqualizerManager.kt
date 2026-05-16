package com.example.ytdown.services

import com.example.ytdown.core.audio.BassFXEngine
import com.example.ytdown.core.audio.PlaybackStateManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor(
    private val fxEngine: BassFXEngine,
    private val stateManager: PlaybackStateManager
) {
    // Frequências padrão para 10 bandas
    private val centerFreqs = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun getNumberOfBands(): Short = 10
    
    fun getBandLevelRange(): Pair<Short, Short> = Pair(-15, 15) // BASS DX8 EQ suporta +/- 15dB
    
    fun getCenterFreq(band: Short): Int = centerFreqs.getOrElse(band.toInt()) { 0 }

    fun getBandLevel(band: Short): Short = (fxEngine.getBandGain(band.toInt()) * 100).toInt().toShort()

    fun setBandLevel(band: Short, level: Short) {
        fxEngine.setBandGain(band.toInt(), level.toFloat() / 100f)
    }

    fun setBassBoostStrength(strength: Short) {
        // Implementação via Ganho nas bandas baixas (ex: 31Hz, 62Hz)
        val gain = (strength.toFloat() / 1000f) * 15f // Escala 0-1000 para 0-15dB
        fxEngine.setBandGain(0, gain)
        fxEngine.setBandGain(1, gain)
    }

    fun getBassBoostStrength(): Short = (fxEngine.getBandGain(0) / 15f * 1000f).toInt().toShort()

    fun setTargetGain(gainmB: Int) {
        // Loudness via BASS Volume
        val volume = 1.0f + (gainmB / 10000f)
        stateManager.updateVolume(volume)
    }

    fun getTargetGain(): Int = ((stateManager.uiState.value.volume - 1.0f) * 10000f).toInt()

    fun release() {
        // BASS gerencia o ciclo de vida via BassCore
    }
}
