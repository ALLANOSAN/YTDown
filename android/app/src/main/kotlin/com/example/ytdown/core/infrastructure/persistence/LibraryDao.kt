package com.example.ytdown.core.infrastructure.persistence

import androidx.room.*
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // --- FAVORITES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    // --- SEARCH HISTORY ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("SELECT `query` FROM search_history ORDER BY createdAt DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<String>>

    // --- PLAYLISTS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = p.id) as trackCount FROM playlists p ORDER BY createdAt DESC")
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("""
        SELECT d.* FROM downloads d
        JOIN playlist_tracks pt ON d.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracks(playlistId: String): Flow<List<DownloadItemEntity>>
}

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val trackCount: Int
)
