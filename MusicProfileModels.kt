package com.example.ytdown.core.domain

import java.time.Instant

data class ListeningStats(
    val totalHours: Double,
    val artistCount: Int,
    val albumCount: Int,
    val dominantGenre: String
)

data class GenreAnalytics(
    val name: String,
    val percentage: Float,
    val color: String // Hex color representation
)

data class ListeningAnalytics(
    val points: List<Pair<Instant, Float>>
)

data class Artist(
    val name: String,
    val playCount: Int,
    val imageUrl: String?
)

data class MusicProfile(
    val stats: ListeningStats,
    val genres: List<GenreAnalytics>,
    val listeningHistory: ListeningAnalytics,
    val topArtists: List<Artist>,
    val score: Int
)

/**
 * FIX: Resolvendo 'Unresolved reference DiscoveryResult'
 * Representa o resultado de uma busca/descoberta de novas bandas/albuns.
 */
data class DiscoveryResult(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val tags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)