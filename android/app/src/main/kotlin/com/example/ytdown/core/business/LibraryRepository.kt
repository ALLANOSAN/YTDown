package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.SongDao
import com.example.ytdown.core.infrastructure.persistence.entities.*
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val downloadDao: DownloadDao,
    private val songDao: SongDao,
    private val metadataManager: DownloadMetadataManager
) {
    // --- Biblioteca Geral ---
    val songs: Flow<List<SongEntity>> = songDao.getAllSongs()

    suspend fun saveSongs(songs: List<SongEntity>) {
        songDao.insertAll(songs)
    }

    // --- Favoritos ---
    fun getFavorites(): Flow<List<FavoriteEntity>> = libraryDao.getAllFavorites()
    
    suspend fun toggleFavorite(favorite: FavoriteEntity) {
        val isFavorite = libraryDao.isFavorite(favorite.id)
        if (isFavorite) {
            libraryDao.deleteFavorite(favorite)
        }
        if (!isFavorite) {
            libraryDao.insertFavorite(favorite)
        }
    }

    suspend fun isFavorite(id: String): Boolean = libraryDao.isFavorite(id)

    // --- Histórico ---
    fun getRecentSearches(): Flow<List<String>> = libraryDao.getRecentSearches()
    
    suspend fun saveSearch(query: String) {
        if (query.isBlank()) return
        libraryDao.insertSearchQuery(SearchHistoryEntity(query = query.trim()))
    }

    suspend fun deleteSearch(query: String) = libraryDao.deleteSearchQuery(query)

    // --- Playlists ---
    fun getPlaylists() = libraryDao.getPlaylistsWithCount()
    
    suspend fun createPlaylist(name: String, description: String? = null) {
        val id = System.currentTimeMillis().toString()
        libraryDao.insertPlaylist(PlaylistEntity(id, name, description, null))
    }

    suspend fun deletePlaylist(id: String) {
        libraryDao.deletePlaylist(id)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val track = PlaylistTrackEntity(playlistId, trackId, 0) 
        libraryDao.addTrackToPlaylist(track)
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        libraryDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getPlaylistTracks(playlistId: String): Flow<List<DownloadItemEntity>> = 
        libraryDao.getPlaylistTracks(playlistId)

    // --- Smart Playlists ---
    fun getRecentlyAdded(): Flow<List<DownloadItemEntity>> = 
        downloadDao.getRecentlyAdded(limit = 50)

}
