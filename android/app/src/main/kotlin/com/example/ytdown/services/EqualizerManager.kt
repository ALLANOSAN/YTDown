package com.example.ytdown.services

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun initEffects(audioSessionId: Int) {
        if (audioSessionId != 0) {
            release()
            try {
                equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
                bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply { enabled = true }
            } catch (e: Exception) { android.util.Log.e("EqualizerManager", "Erro init: ${e.message}") }
        }
    }

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0
    fun getBandLevelRange(): Pair<Short, Short> {
        val range = equalizer?.bandLevelRange ?: return Pair(0, 0)
        return Pair(range[0], range[1])
    }
    fun getCenterFreq(band: Short): Int = equalizer?.getCenterFreq(band) ?: 0
    fun getBandLevel(band: Short): Short = equalizer?.getBandLevel(band) ?: 0
    fun setBandLevel(band: Short, level: Short) { equalizer?.setBandLevel(band, level) }
    fun setBassBoostStrength(strength: Short) { bassBoost?.setStrength(strength) }
    fun getBassBoostStrength(): Short = bassBoost?.roundedStrength ?: 0
    fun setTargetGain(gainmB: Int) { loudnessEnhancer?.setTargetGain(gainmB) }
    fun getTargetGain(): Int = loudnessEnhancer?.targetGain?.toInt() ?: 0

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        loudnessEnhancer?.release(); loudnessEnhancer = null
    }
}
