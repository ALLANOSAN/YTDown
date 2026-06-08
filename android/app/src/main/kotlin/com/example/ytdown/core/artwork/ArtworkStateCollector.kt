package com.example.ytdown.core.artwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import com.example.ytdown.ui.PlaybackUiState

@Singleton
class ArtworkStateCollector @Inject constructor(
    private val rotationController: ArtworkRotationController,
    private val cacheManager: ArtworkCacheManager
) {

    fun getArtworkState(
        scope: CoroutineScope,
        playbackUiState: StateFlow<PlaybackUiState>
    ): StateFlow<ArtworkState> {
        return combine(
            playbackUiState,
            rotationController.artworkMode
        ) { uiState, mode ->
            val track = uiState.currentTrack
            if (track == null) {
                ArtworkState(null, mode)
            } else {
                val image = if (mode == ArtworkMode.ALBUM) {
                    val cacheKey = cacheManager.getCacheKey(track.artist ?: "", track.album ?: "")
                    cacheManager.getCachedAlbumArt(cacheKey)?.absolutePath
                } else {
                    val cacheKey = cacheManager.getArtistCacheKey(track.artist ?: "")
                    cacheManager.getCachedArtistArt(cacheKey)?.absolutePath
                }
                ArtworkState(image, mode)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), ArtworkState())
    }
}
