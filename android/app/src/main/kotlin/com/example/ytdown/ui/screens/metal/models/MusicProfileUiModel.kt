package com.example.ytdown.ui.screens.metal.models

import com.example.ytdown.data.local.metal.entities.MusicProfileEntity

/**
 * UI Model para o Dashboard de Perfil Musical
 * Separação clara entre camada de dados e UI
 */
data class MusicProfileUiModel(
    val totalListeningHours: String,
    val totalTracksPlayed: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val musicScore: Int,
    val scoreDescription: String,
    val favoriteGenres: List<GenreUiModel>,
    val topArtists: List<ArtistUiModel>,
    val weeklyData: Map<String, Int>,
    val isLoading: Boolean = false,
    val hasData: Boolean = false
)

/**
 * UI Model para gênero no gráfico
 */
data class GenreUiModel(
    val name: String,
    val percentage: Float,
    val color: Long
)

/**
 * UI Model para artista no gráfico
 */
data class ArtistUiModel(
    val rank: Int,
    val name: String,
    val playCount: Int,
    val progress: Float // 0.0 - 1.0
)

/**
 * UI Model para ponto de evolução semanal
 */
data class WeeklyPointUiModel(
    val day: String,
    val playCount: Int,
    val heightDp: Float
)

/**
 * Estado do Dashboard
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Success(val profile: MusicProfileUiModel) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

/**
 * Cores para os gêneros nos gráficos
 */
object GenreColors {
    private val colors = listOf(
        0xFF9C27B0, // Purple
        0xFFE91E63, // Pink
        0xFF2196F3, // Blue
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFF00BCD4, // Cyan
        0xFFFF5722, // Deep Orange
        0xFF795548  // Brown
    )
    
    fun getColor(index: Int): Long = colors[index % colors.size]
}

/**
 * Mapper: MusicProfileEntity -> MusicProfileUiModel
 */
fun MusicProfileEntity.toUiModel(): MusicProfileUiModel {
    val genres = getDominantGenres()
    val artists = getFavoriteArtists()
    
    return MusicProfileUiModel(
        totalListeningHours = formatHours(totalListenTimeMs),
        totalTracksPlayed = totalTracksPlayed,
        uniqueArtists = uniqueArtistsCount,
        uniqueAlbums = uniqueAlbumsCount,
        musicScore = musicScore.toInt().coerceIn(0, 100),
        scoreDescription = getScoreDescription(musicScore),
        favoriteGenres = genres.mapIndexed { index, genre ->
            GenreUiModel(
                name = genre.genre,
                percentage = genre.percentage,
                color = GenreColors.getColor(index)
            )
        },
        topArtists = artists.take(8).mapIndexed { index, artist ->
            val maxPlays = artists.maxOfOrNull { it.playCount } ?: 1
            ArtistUiModel(
                rank = index + 1,
                name = artist.artistName,
                playCount = artist.playCount,
                progress = if (maxPlays > 0) artist.playCount.toFloat() / maxPlays else 0f
            )
        },
        weeklyData = getWeeklyEvolution(),
        hasData = totalTracksPlayed > 0 || uniqueArtistsCount > 0
    )
}

private fun formatHours(ms: Long): String {
    val hours = ms / (1000 * 60 * 60)
    val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun getScoreDescription(score: Double): String = when {
    score >= 80 -> "Explorador Incansável"
    score >= 60 -> "Entusiasta Variado"
    score >= 40 -> "Ouvinte Consistente"
    score >= 20 -> "Descobridor Moderado"
    else -> "Iniciante"
}

/**
 * Mapper para ListeningStatsResult
 */
fun com.example.ytdown.data.repository.metal.ListeningStatsResult.toUiModel(): MusicProfileUiModel {
    val maxArtistPlays = topArtists.maxOfOrNull { it.playCount } ?: 1
    
    return MusicProfileUiModel(
        totalListeningHours = formatHours(totalListenTimeMs),
        totalTracksPlayed = totalPlays,
        uniqueArtists = uniqueArtists,
        uniqueAlbums = uniqueAlbums,
        musicScore = calculateScore(),
        scoreDescription = getScoreDescription(calculateScore().toDouble()),
        favoriteGenres = genreDistribution.mapIndexed { index, genre ->
            GenreUiModel(
                name = genre.genre,
                percentage = if (totalPlays > 0) genre.playCount.toFloat() / totalPlays * 100 else 0f,
                color = GenreColors.getColor(index)
            )
        },
        topArtists = topArtists.take(8).mapIndexed { index, artist ->
            ArtistUiModel(
                rank = index + 1,
                name = artist.name,
                playCount = artist.playCount,
                progress = artist.playCount.toFloat() / maxArtistPlays
            )
        },
        weeklyData = dailyDistribution.mapKeys { getDayName(it.key) },
        hasData = totalPlays > 0
    )
}

private fun calculateScore(): Int {
    // Calcula score baseado nas estatísticas
    return 50 // Placeholder - pode ser melhorado
}

private fun getDayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Dom"
    2 -> "Seg"
    3 -> "Ter"
    4 -> "Qua"
    5 -> "Qui"
    6 -> "Sex"
    7 -> "Sáb"
    else -> "?"
}