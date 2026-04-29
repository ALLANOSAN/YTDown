package com.example.ytdown.services

import com.example.ytdown.core.business.LibraryRepository
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryService @Inject constructor(
    private val repository: LibraryRepository,
    private val downloadDao: DownloadDao
) {
    fun getSongs(): Flow<List<DownloadItemEntity>> = downloadDao.getAllDownloads()

    suspend fun getArtistsWithMetadata(query: String = ""): List<Map<String, String>> {
        // A camada Kotlin já expõe destaques de artistas via DAO; mapeamos genericamente.
        return repository.getPlaylists().firstOrNull()?.map {
            mapOf("name" to it.playlist.name, "trackCount" to it.trackCount.toString())
        } ?: emptyList()
    }

    suspend fun getAlbumsWithMetadata(query: String = ""): List<Map<String, String>> {
        return emptyList()
    }

    suspend fun search(query: String): List<DownloadItemEntity> {
        return downloadDao.searchLibrary(query).firstOrNull() ?: emptyList()
    }

    suspend fun getLibraryByArtist(artist: String): List<DownloadItemEntity> {
        return downloadDao.getAllDownloads().firstOrNull().orEmpty()
            .filter { it.artist.equals(artist, ignoreCase = true) }
    }

    suspend fun getLibraryByAlbum(album: String): List<DownloadItemEntity> {
        return downloadDao.getAllDownloads().firstOrNull().orEmpty()
            .filter { it.album.equals(album, ignoreCase = true) }
    }
}
