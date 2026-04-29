package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val isLoading: Boolean = false,
    val isProcessingRequest: Boolean = false,
    val error: String? = null,
    val currentUrl: String = ""
)

class HomeProvider : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun setLoading(isLoading: Boolean) {
        _state.value = _state.value.copy(isLoading = isLoading)
    }

    fun setProcessing(isProcessing: Boolean) {
        _state.value = _state.value.copy(isProcessingRequest = isProcessing)
    }

    fun setError(error: String?) {
        _state.value = _state.value.copy(error = error)
    }

    fun setUrl(url: String) {
        _state.value = _state.value.copy(currentUrl = url, error = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
