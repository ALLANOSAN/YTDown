package com.example.ytdown.core.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

class EqualizerViewModel @Inject constructor(
    private val fxEngine: BassFXEngine,
    private val playbackEngine: BassPlaybackEngine,
    private val repository: EqualizerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fxEngine.setupEqualizer()
        startSpectrumAnalyzer()
        
        // Carrega estados salvos
        viewModelScope.launch {
            repository.savedGains.collect { savedGains ->
                _uiState.update { it.copy(bandGains = savedGains) }
            }
        }
    }

    private fun startSpectrumAnalyzer() {
        viewModelScope.launch {
            val buffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder())
            while (isActive) {
                playbackEngine.getFftData(buffer)
                val floats = FloatArray(128)
                buffer.asFloatBuffer().get(floats)
                _uiState.update { it.copy(spectrumData = floats) }
                delay(50) // 20fps
            }
        }
    }

    fun updateBandGain(index: Int, gain: Float) {
        val newGains = _uiState.value.bandGains.copyOf()
        newGains[index] = gain.coerceIn(-15f, 15f)
        
        _uiState.update { it.copy(bandGains = newGains) }
        fxEngine.setBandGain(index, newGains[index])
        
        viewModelScope.launch { repository.saveBandGain(index, newGains[index]) }
    }
}
