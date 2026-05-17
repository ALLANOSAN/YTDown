package com.example.ytdown.core.infrastructure

import androidx.compose.runtime.Immutable

/**
 * EqualizerUiState - Estado reativo da UI do Equalizador.
 * Versão unificada para uso no EqualizerViewModel.
 */
@Immutable
data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val bands: List<Float> = List(10) { 0f },  // 10 bandas
    val preamp: Float = 0f,
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val currentPresetName: String = "Flat",
    val presets: List<EqualizerPreset> = EqualizerPreset.DefaultPresets
) {
    val frequencies = listOf(
        "31Hz", "62Hz", "125Hz", "250Hz", "500Hz",
        "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
    )
}

/**
 * EqualizerPreset - Representa um preset de equalização profissional.
 * Versão com companion object para presets padrão.
 */
@Immutable
data class EqualizerPreset(
    val name: String,
    val gains: List<Float>  // 10 bandas
) {
    companion object {
        val Flat = EqualizerPreset("Flat", List(10) { 0f })
        
        val Rock = EqualizerPreset("Rock", listOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f))
        
        val HeavyMetal = EqualizerPreset("Heavy Metal", listOf(4f, 3f, 1f, 0f, 0f, 0f, 1f, 2f, 4f, 5f))
        
        val DeathMetal = EqualizerPreset("Death Metal", listOf(6f, 4f, 2f, 0f, -2f, -2f, 1f, 3f, 5f, 4f))
        
        val BlackMetal = EqualizerPreset("Black Metal", listOf(-2f, -1f, 0f, 1f, 2f, 3f, 5f, 6f, 7f, 6f))
        
        val BassBoost = EqualizerPreset("Bass Boost", listOf(7f, 5f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))

        val DefaultPresets = listOf(
            Flat, Rock, HeavyMetal, DeathMetal, BlackMetal,
            EqualizerPreset("Jazz", listOf(3f, 2f, 0f, 1f, 1f, 1f, 0f, 1f, 2f, 3f)),
            EqualizerPreset("Classical", listOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 2f, 3f, 3f)),
            EqualizerPreset("Electronic", listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f)),
            EqualizerPreset("Vocal", listOf(-2f, -3f, -2f, 1f, 3f, 3f, 2f, 1f, -1f, -2f)),
            BassBoost,
            EqualizerPreset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 5f, 7f, 9f))
        )
    }
}