package com.example.ytdown.core.artwork

/**
 * Representa o estado visual da arte de capa/artista.
 */
data class ArtworkState(
    val currentImage: String? = null, // Pode ser URI de arquivo local ou cache
    val mode: ArtworkMode = ArtworkMode.ALBUM
)

enum class ArtworkMode {
    ALBUM,
    ARTIST
}
