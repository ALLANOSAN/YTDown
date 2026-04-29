package com.example.ytdown.services

import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseService @Inject constructor(
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository
) {
    suspend fun insertDownload(item: DownloadItemEntity) = downloadDao.upsert(item)
    suspend fun updateDownload(item: DownloadItemEntity) = downloadDao.update(item)
    suspend fun getAllDownloads(): List<DownloadItemEntity> = downloadDao.getAllDownloadsSync()
    suspend fun getPlaylistTracks(playlistId: String): List<DownloadItemEntity> =
        libraryRepository.getPlaylistTracks(playlistId).first()
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) =
        libraryRepository.addTrackToPlaylist(playlistId, trackId)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) =
        libraryDao.removeTrackFromPlaylist(playlistId, trackId)
    suspend fun getPlaylists(): List<PlaylistWithCount> = libraryRepository.getPlaylists().first()
    suspend fun createPlaylist(name: String, description: String? = null) =
        libraryRepository.createPlaylist(name, description)
    suspend fun getLibraryAudios(): List<DownloadItemEntity> =
        downloadDao.getAllDownloadsSync().filter { it.type == 0 && it.status == "completed" }
    suspend fun getDistinctArtists(): List<String> = downloadDao.getDistinctArtists().first()
    suspend fun getDistinctAlbums(): List<String> = downloadDao.getDistinctAlbums().first()
    suspend fun searchLibrary(query: String): List<DownloadItemEntity> =
        downloadDao.searchLibrary(query).first()
    suspend fun getLibraryByArtist(artist: String): List<DownloadItemEntity> =
        getAllDownloads().filter { it.artist.equals(artist, ignoreCase = true) }
    suspend fun getLibraryByAlbum(album: String): List<DownloadItemEntity> =
        getAllDownloads().filter { it.album.equals(album, ignoreCase = true) }
    suspend fun getRecentSearches(): List<String> = libraryRepository.getRecentSearches().first()
    suspend fun saveSearch(query: String) = libraryRepository.saveSearch(query)
    suspend fun deleteSearch(query: String) = libraryRepository.deleteSearch(query)
    suspend fun getFavorites() = libraryRepository.getFavorites().first()
}
