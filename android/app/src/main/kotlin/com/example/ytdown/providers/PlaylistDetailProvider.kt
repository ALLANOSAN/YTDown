package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.VideoPreviewItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailProvider @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _playlistItems = MutableStateFlow<List<VideoPreviewItem>>(emptyList())
    val playlistItems = _playlistItems.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    /**
     * Filtra itens da playlist em tempo real conforme a busca do usuário.
     */
    val filteredItems = combine(_playlistItems, _searchQuery) { items, query ->
        var filtered = items
        if (query.isNotBlank()) {
            filtered = items.filter { it.title.value.lowercase().contains(query.lowercase()) }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPlaylistItems(items: List<VideoPreviewItem>) {
        _playlistItems.value = items
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(item: VideoPreviewItem) {
        val updated = _playlistItems.value.map {
            var candidate = it
            if (it.id == item.id) {
                candidate = it.copy(isSelected = !it.isSelected)
            }
            candidate
        }
        _playlistItems.value = updated
    }

    fun selectAll(select: Boolean) {
        val updated = _playlistItems.value.map { it.copy(isSelected = select) }
        _playlistItems.value = updated
    }
}
