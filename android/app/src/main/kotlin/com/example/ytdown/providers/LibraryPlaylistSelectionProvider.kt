package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibraryPlaylistSelectionState(
    val selectedTrackIds: Set<String> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedTrackIds.isNotEmpty()
    val selectedCount: Int get() = selectedTrackIds.size
}

class LibraryPlaylistSelectionProvider : ViewModel() {
    private val _state = MutableStateFlow(LibraryPlaylistSelectionState())
    val state: StateFlow<LibraryPlaylistSelectionState> = _state.asStateFlow()

    fun selectOnly(trackId: String) {
        _state.value = LibraryPlaylistSelectionState(selectedTrackIds = setOf(trackId))
    }

    fun toggleSelection(trackId: String) {
        val current = _state.value.selectedTrackIds
        var selectedTrackIds = current + trackId
        if (current.contains(trackId)) {
            selectedTrackIds = current - trackId
        }
        _state.value = _state.value.copy(selectedTrackIds = selectedTrackIds)
    }

    fun clearSelection() {
        _state.value = LibraryPlaylistSelectionState()
    }
}
