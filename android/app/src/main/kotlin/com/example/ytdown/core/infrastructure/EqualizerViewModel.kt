package com.example.ytdown.core.infrastructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.audio.BassFXEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val fxEngine: BassFXEngine,
    private val equalizerSettingsDataStore: EqualizerSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        // Carrega as configurações salvas na inicialização do ViewModel
        loadSavedSettings()
        
        // Observa mudanças no uiState e salva automaticamente
        viewModelScope.launch {
            _uiState.collect { state ->
                equalizerSettingsDataStore.saveSettings(state)
            }
        }
    }

    private fun loadSavedSettings() {
        // Aqui você carregaria do DataStore
        // Simulando carregamento inicial
        fxEngine.setupEqualizer()
        viewModelScope.launch {
            val savedState = equalizerSettingsDataStore.loadSettings()
            _uiState.value = savedState
            applyAllGains() // Aplica os ganhos carregados ao motor FX
            // TODO: Aplicar preamp, bass boost, virtualizer ao fxEngine se implementados
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        // Nota: O BassFXEngine deve tratar o bypass internamente ou zerar ganhos
        applyAllGains()
    }

    fun updateBand(index: Int, gain: Float) {
        if (index !in 0 until 10) return
        
        val newBands = _uiState.value.bands.toMutableList()
        newBands[index] = gain
        
        _uiState.update { it.copy(bands = newBands, currentPresetName = "Custom") }
        
        // Integração Realtime: Aplica no motor BASS imediatamente
        if (_uiState.value.isEnabled) {
            fxEngine.setBandGain(index, gain)
        }
        // A chamada a saveSettings é acionada pelo collect no init
    }

    fun updatePreamp(gain: Float) {
        _uiState.update { it.copy(preamp = gain) }
        // fxEngine.setPreamp(gain) // Implementar no engine se disponível
        // A chamada a saveSettings é acionada pelo collect no init
    }

    fun updateBassBoost(value: Float) {
        _uiState.update { it.copy(bassBoost = value) }
        // fxEngine.setBassBoost(value)
        // A chamada a saveSettings é acionada pelo collect no init
    }

    fun updateVirtualizer(value: Float) {
        _uiState.update { it.copy(virtualizer = value) }
        // fxEngine.setVirtualizer(value)
        // A chamada a saveSettings é acionada pelo collect no init
    }

    fun applyPreset(preset: EqualizerPreset) {
        _uiState.update { it.copy(bands = preset.gains, currentPresetName = preset.name) }
        applyAllGains()
    }

    fun resetToFlat() {
        applyPreset(EqualizerPreset.Flat)
    }

    private fun applyAllGains() {
        val state = _uiState.value
        state.bands.forEachIndexed { index, gain ->
            val finalGain = if (state.isEnabled) gain else 0f
            fxEngine.setBandGain(index, finalGain)
        }
        // Não é necessário chamar saveSettings aqui, pois _uiState.update já aciona o collect.
    }
    
    /**
     * Provê dados FFT para o visualizador da UI
     */
    fun getFftData(buffer: java.nio.ByteBuffer) {
        playerManager.getAudioEngine().getFftData(buffer)
    }
}