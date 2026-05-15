package com.example.ytdown.core.infrastructure.mappers

import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.persistence.projections.*
import com.example.ytdown.ui.models.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converte Projeção do Banco (DTO) para Modelo de Domínio. */
fun ListeningStatsProjection.toDomain(): ListeningStats {
    return ListeningStats(
            totalHours = totalMillis / 3600000.0,
            artistCount = uniqueArtistsCount,
            albumCount = uniqueAlbumsCount,
            dominantGenre = topGenre ?: "Unknown"
    )
}

/**
 * Converte Modelo de Domínio Agregado para UI Model específico do Dashboard. Resolve o erro de
 * candidatos de mapeamento na UI.
 */
fun MusicProfile.toUiModel(): MusicProfileUiModel {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    return MusicProfileUiModel(
            totalListeningHours =
                    String.format(Locale.getDefault(), "%.1f h", this.stats.totalHours),
            favoriteGenres =
                    this.genres.map { domain ->
                        GenreUiModel(
                                label = domain.name,
                                value =
                                        String.format(
                                                Locale.getDefault(),
                                                "%.0f%%",
                                                domain.percentage * 100
                                        ),
                                color =
                                        try {
                                            android.graphics.Color.parseColor(domain.color).toLong()
                                        } catch (e: Exception) {
                                            0xFF808080 // Default gray
                                        }
                        )
                    },
            topArtists =
                    this.topArtists.map { artist ->
                        ArtistUiModel(
                                name = artist.name,
                                playCount = "${artist.playCount} plays",
                                imageUrl = artist.imageUrl
                        )
                    },
            listeningTrend =
                    this.listeningHistory.points.mapIndexed { index, pair ->
                        ListeningPointUi(
                                x = index.toFloat(),
                                y = pair.second,
                                label = timeFormatter.format(pair.first)
                        )
                    },
            score = this.score
    )
}
