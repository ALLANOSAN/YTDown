package com.example.ytdown.providers

import com.example.ytdown.utils.YouTubeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserState(
    val currentUrl: String = BrowserState.defaultYouTubeUrl,
    val isYoutube: Boolean = true,
    val isLoading: Boolean = false,
    val progress: Double = 0.0,
    val isInitialLoad: Boolean = true,
    val hasShownError: Boolean = false
) {
    companion object {
        const val defaultYouTubeUrl = "https://m.youtube.com"
    }

    fun copyWith(
        currentUrl: String? = null,
        isYoutube: Boolean? = null,
        isLoading: Boolean? = null,
        progress: Double? = null,
        isInitialLoad: Boolean? = null,
        hasShownError: Boolean? = null
    ): BrowserState {
        return BrowserState(
            currentUrl = currentUrl ?: this.currentUrl,
            isYoutube = isYoutube ?: this.isYoutube,
            isLoading = isLoading ?: this.isLoading,
            progress = progress ?: this.progress,
            isInitialLoad = isInitialLoad ?: this.isInitialLoad,
            hasShownError = hasShownError ?: this.hasShownError
        )
    }
}

class BrowserProvider {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    fun setUrl(url: String) {
        // Apenas atualiza a URL e o estado Youtube, sem resetar isInitialLoad ou hasShownError
        _state.value = _state.value.copyWith(
            currentUrl = url,
            isYoutube = isYouTubeUrl(url),
            progress = 0.0
        )
    }

    fun setLoading(isLoading: Boolean) {
        _state.value = _state.value.copyWith(isLoading = isLoading)
    }

    fun setProgress(progress: Double) {
        _state.value = _state.value.copyWith(progress = progress)
    }

    fun setInitialLoad(isInitialLoad: Boolean) {
        _state.value = _state.value.copyWith(isInitialLoad = isInitialLoad)
    }

    fun setHasShownError(hasShownError: Boolean) {
        _state.value = _state.value.copyWith(hasShownError = hasShownError)
    }

    fun reset() {
        _state.value = BrowserState()
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return YouTubeUtils.isYouTubeUrl(url)
    }
}

val browserProvider = BrowserProvider()
