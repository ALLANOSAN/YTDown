package com.example.ytdown.core.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SongMetadataOverrideEntity - Camada de Overrides do Usuário
 * 
 * Armazena as correções manuais feitas pelo usuário que devem
 * prevalecer sobre as tags reais do arquivo e sobre qualquer rescan.
 */
@Entity(tableName = "song_metadata_overrides")
data class SongMetadataOverrideEntity(
    @PrimaryKey
    val songPath: String, // Caminho absoluto ou URI que identifica o arquivo de forma única

    val overriddenArtist: String?,
    val overriddenTitle: String?,
    val overriddenAlbum: String?,
    val overriddenGenre: String?,

    val updatedAt: Long = System.currentTimeMillis()
)