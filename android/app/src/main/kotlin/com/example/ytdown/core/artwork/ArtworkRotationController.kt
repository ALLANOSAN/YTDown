package com.example.ytdown.core.artwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkRotationController @Inject constructor() {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _artworkMode = MutableStateFlow(ArtworkMode.ALBUM)
    val artworkMode: StateFlow<ArtworkMode> = _artworkMode.asStateFlow()

    init {
        startRotation()
    }

    private fun startRotation() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                _artworkMode.value = if (_artworkMode.value == ArtworkMode.ALBUM) {
                    ArtworkMode.ARTIST
                } else {
                    ArtworkMode.ALBUM
                }
            }
        }
    }
}
