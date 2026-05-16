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
}
