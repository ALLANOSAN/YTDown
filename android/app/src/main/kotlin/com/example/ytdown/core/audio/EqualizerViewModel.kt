package com.example.ytdown.core.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.infrastructure.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val fxEngine: BassFXEngine,
    private val settingsDataStore: EqualizerSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        loadSavedSettings()
        startSpectrumAnalyzer()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val saved = settingsDataStore.loadSettings()
            _uiState.value = saved
            fxEngine.setupEqualizer()
            applyAllGains()
        }
    }

    private fun startSpectrumAnalyzer() {
        viewModelScope.launch {
            val buffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder())
            while (true) {
                val engine = playerManager.getAudioEngine()
                if (engine.getActiveChannel() != 0) {
                    engine.getFftData(buffer)
                    buffer.rewind()
                    
                    val fftData = FloatArray(64)
                    for (i in 0 until 64) {
                        // Captura e suavização básica de amplitude
                        val magnitude = buffer.float
                        fftData[i] = (magnitude * 10f).coerceIn(0f, 1f)
                    }
                    _uiState.update { it.copy(spectrumData = fftData) }
                }
                delay(16) // ~60fps
            }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        applyAllGains()
        saveSettings()
    }

    fun updateBand(index: Int, gain: Float) {
        if (index !in 0 until 10) return
        
        val newBands = _uiState.value.bandGains.copyOf()
        newBands[index] = gain
        
        _uiState.update { it.copy(bandGains = newBands, currentPresetId = "custom") }
        
        if (_uiState.value.isEnabled) {
            fxEngine.setBandGain(index, gain)
        }
        saveSettings()
    }

    // Alias para compatibilidade com a UI
    fun setBandGain(index: Int, gain: Float) {
        updateBand(index, gain)
    }

    fun updatePreamp(gain: Float) {
        _uiState.update { it.copy(preamp = gain) }
        fxEngine.setPreamp(gain)
        saveSettings()
    }

    fun updateBassBoost(value: Float) {
        // _uiState.update { it.copy(bassBoost = value) }
        saveSettings()
    }

    fun updateVirtualizer(value: Float) {
        // _uiState.update { it.copy(virtualizer = value) }
        saveSettings()
    }

    fun applyPreset(preset: EqualizerPreset) {
        _uiState.update { it.copy(bandGains = preset.gains.clone(), currentPresetId = preset.id) }
        applyAllGains()
        saveSettings()
    }

    private fun applyAllGains() {
        val state = _uiState.value
        state.bandGains.forEachIndexed { index, gain ->
            val finalGain = if (state.isEnabled) gain else 0f
            fxEngine.setBandGain(index, finalGain)
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            settingsDataStore.saveSettings(_uiState.value)
        }
    }
}