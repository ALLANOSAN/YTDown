package com.example.ytdown.core.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ArtistImageCacheEntity - Cache Inteligente de Imagens de Artista
 * 
 * Imagens de artista são armazenadas separadamente para não inflar as tags
 * do arquivo e permitir gestão centralizada (uma foto para todas as músicas do artista).
 */
@Entity(tableName = "artist_image_cache")
data class ArtistImageCacheEntity(
    @PrimaryKey
    val artistName: String, // Chave primária é o nome do artista (normalizado)

    val imageUrl: String?,
    val localCachePath: String?,
    val updatedAt: Long = System.currentTimeMillis()
)