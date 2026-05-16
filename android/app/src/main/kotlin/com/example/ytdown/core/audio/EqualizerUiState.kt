package com.example.ytdown.core.audio

/**
 * Estado atual da UI do Equalizador.
 */
data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val preamp: Float = 0f,
    val bandGains: FloatArray = FloatArray(10) { 0f },
    val currentPresetId: String = "flat",
    val spectrumData: FloatArray = FloatArray(128)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerUiState) return false
        if (isEnabled != other.isEnabled) return false
        if (preamp != other.preamp) return false
        if (!bandGains.contentEquals(other.bandGains)) return false
        if (currentPresetId != other.currentPresetId) return false
        if (!spectrumData.contentEquals(other.spectrumData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isEnabled.hashCode()
        result = 31 * result + preamp.hashCode()
        result = 31 * result + bandGains.contentHashCode()
        result = 31 * result + currentPresetId.hashCode()
        result = 31 * result + spectrumData.contentHashCode()
        return result
    }
}
