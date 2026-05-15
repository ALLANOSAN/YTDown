package com.example.ytdown.data.local.metal.dao

import androidx.room.*
import com.example.ytdown.data.local.metal.entities.MusicProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para Perfil Musical do usuário
 */
@Dao
interface MusicProfileDao {

    /**
     * Observa o perfil atual (sempre ID = 1)
     */
    @Query("SELECT * FROM music_profile WHERE isActive = 1")
    fun observeProfile(): Flow<MusicProfileEntity?>
    
    /**
     * Busca o perfil atual
     */
    @Query("SELECT * FROM music_profile WHERE isActive = 1")
    suspend fun getProfile(): MusicProfileEntity?
    
    /**
     * Salva/ atualiza o perfil
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: MusicProfileEntity)
    
    /**
     * Atualiza parcialmente o perfil (apenas campos específicos)
     */
    @Query("""
        UPDATE music_profile
        SET dominantGenresJson = :genresJson,
            favoriteArtistsJson = :artistsJson,
            totalListenTimeMs = :totalTime,
            totalTracksPlayed = :totalTracks,
            uniqueArtistsCount = :uniqueArtists,
            uniqueAlbumsCount = :uniqueAlbums,
            musicScore = :score,
            lastUpdatedAt = :timestamp,
            generatedAt = :timestamp
        WHERE id = 1
    """)
    suspend fun updateProfile(
        genresJson: String,
        artistsJson: String,
        totalTime: Long,
        totalTracks: Int,
        uniqueArtists: Int,
        uniqueAlbums: Int,
        score: Double,
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Deleta o perfil
     */
    @Query("DELETE FROM music_profile")
    suspend fun deleteProfile()
}