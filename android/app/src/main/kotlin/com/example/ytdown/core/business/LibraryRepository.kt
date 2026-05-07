package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
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
    private val metadataManager: DownloadMetadataManager
) {
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

    // --- Edição em Cascata (Lote) ---

    /**
     * Atualiza o nome e a foto de um ARTISTA em todos os seus arquivos.
     */
    suspend fun updateArtistInBatch(oldName: String, newName: String, localPhotoPath: String?) {
        val allDownloads = downloadDao.getAllDownloadsSync()
        val artistTracks = allDownloads.filter { it.artist?.equals(oldName, ignoreCase = true) == true }

        artistTracks.forEach { track ->
            val updated = track.copy(artist = newName, artistImageUrl = localPhotoPath ?: track.artistImageUrl)
            downloadDao.update(updated)
            
            // Regrava a tag física no arquivo, incluindo artwork se uma imagem da galeria foi selecionada.
            val targetPath = track.exportedPath?.takeIf { it.isNotBlank() } ?: track.outputPath
            metadataManager.rewriteMetadata(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    title = MediaTitle(track.title),
                    artist = ArtistName(newName),
                    album = AlbumName(track.album ?: "")
                ),
                exportedPath = track.exportedPath,
                artworkUrl = localPhotoPath
            )
        }
    }

    /**
     * Atualiza o nome e a foto de um ÁLBUM em todos os seus arquivos.
     * Mantém intactos os campos de ARTISTA para esses arquivos.
     */
    suspend fun updateAlbumInBatch(artist: String? = null, oldAlbum: String, newAlbum: String, localPhotoPath: String?) {
        val allDownloads = downloadDao.getAllDownloadsSync()
        val albumTracks = allDownloads.filter {
            it.album?.equals(oldAlbum, ignoreCase = true) == true &&
            (artist.isNullOrBlank() || it.artist?.equals(artist, ignoreCase = true) == true)
        }

        albumTracks.forEach { track ->
            val updated = track.copy(album = newAlbum, albumImageUrl = localPhotoPath ?: track.albumImageUrl)
            downloadDao.update(updated)
            
            // Regrava a tag física no arquivo, incluindo artwork se uma imagem da galeria foi selecionada.
            val targetPath = track.exportedPath?.takeIf { it.isNotBlank() } ?: track.outputPath
            metadataManager.rewriteMetadata(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    title = MediaTitle(track.title),
                    artist = ArtistName(track.artist ?: ""),
                    album = AlbumName(newAlbum)
                ),
                exportedPath = track.exportedPath,
                artworkUrl = localPhotoPath
            )
        }
    }
}
