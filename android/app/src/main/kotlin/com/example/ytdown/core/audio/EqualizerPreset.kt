package com.example.ytdown.core.audio

/**
 * Representa um preset de equalização profissional.
 */
data class EqualizerPreset(
    val id: String,
    val name: String,
    val gains: FloatArray // 10 bandas
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerPreset) return false
        if (id != other.id) return false
        if (!gains.contentEquals(other.gains)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + gains.contentHashCode()
        return result
    }

    companion object {
        val Flat = EqualizerPreset("flat", "Flat", FloatArray(10) { 0f })
        val Rock = EqualizerPreset("rock", "Rock", floatArrayOf(2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 3f))
        val Metal = EqualizerPreset("metal", "Metal", floatArrayOf(3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f, 4f))
        val BassBoost = EqualizerPreset("bass_boost", "Bass Boost", floatArrayOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f))
    }
}
