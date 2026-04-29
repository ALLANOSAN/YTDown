package com.example.ytdown.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryProvider @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao
) : ViewModel() {

    val completedAudioTracks: StateFlow<List<DownloadItemEntity>> = downloadRepository
        .stream()
        .map { list ->
            list.filter { it.type == 0 && it.status == "completed" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<String>> = downloadDao.getDistinctArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<String>> = downloadDao.getDistinctAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun searchLibrary(query: String): Flow<List<DownloadItemEntity>> {
        return downloadDao.searchLibrary(query)
    }

    fun getPlaylistTracks(playlistId: String) = libraryRepository.getPlaylistTracks(playlistId)

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            libraryRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            libraryDao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }
}
