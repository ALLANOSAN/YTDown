package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryPlaylistsProvider @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val playlists = libraryRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // A lista é reativa via Room; apenas forçar leitura se necessário.
                libraryRepository.getPlaylists()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            libraryRepository.createPlaylist(name, description)
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            trackIds.forEach { libraryRepository.addTrackToPlaylist(playlistId, it) }
        }
    }
}
