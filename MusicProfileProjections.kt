package com.example.ytdown.core.infrastructure.persistence.projections

/**
 * Projeção para estatísticas de audição agregadas do Room.
 */
data class ListeningStatsProjection(
    val totalMillis: Long,
    val uniqueArtistsCount: Int,
    val uniqueAlbumsCount: Int,
    val topGenre: String?
)

/**
 * Projeção para distribuição de gêneros.
 */
data class GenreDistributionProjection(
    val genreName: String,
    val occurrences: Int
)