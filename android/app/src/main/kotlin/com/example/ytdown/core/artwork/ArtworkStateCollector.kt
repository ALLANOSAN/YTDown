package com.example.ytdown.core.artwork

import com.example.ytdown.ui.PlaybackViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkStateCollector @Inject constructor(
    private val playbackViewModel: PlaybackViewModel,
    private val rotationController: ArtworkRotationController,
    private val cacheManager: ArtworkCacheManager
) {

    fun getArtworkState(scope: CoroutineScope): StateFlow<ArtworkState> {
        return combine(
            playbackViewModel.playbackUiState,
            rotationController.artworkMode
        ) { uiState, mode ->
            val track = uiState.currentTrack
            if (track == null) {
                ArtworkState(null, mode)
            } else {
                val image = if (mode == ArtworkMode.ALBUM) {
                    cacheManager.getAlbumArtworkPath(track.artist ?: "", track.album ?: "")
                } else {
                    cacheManager.getArtistArtworkPath(track.artist ?: "")
                }
                ArtworkState(image, mode)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), ArtworkState())
    }
}
