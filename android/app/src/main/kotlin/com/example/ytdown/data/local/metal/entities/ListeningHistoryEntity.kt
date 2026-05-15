package com.example.ytdown.data.local.metal.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade de Histórico de Escuta - Sistema Metal
 * 
 * Registra toda a atividade de reprodução do usuário para:
 * - Análise de padrões de escuta
 * - Geração de perfil musical dinâmico
 * - Recomendações personalizadas
 * - Estatísticas de uso
 */
@Entity(
    tableName = "listening_history",
    indices = [
        androidx.room.Index(value = ["artistName"]),
        androidx.room.Index(value = ["albumName"]),
        androidx.room.Index(value = ["listenedAt"]),
        androidx.room.Index(value = ["genre"]),
        androidx.room.Index(value = ["hourOfDay"]),
        androidx.room.Index(value = ["dayOfWeek"])
    ]
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Identificação do conteúdo
    val artistMbid: String? = null,
    val artistName: String,
    val albumMbid: String? = null,
    val albumName: String? = null,
    val trackName: String? = null,
    val trackMbid: String? = null,
    
    // Metadados do conteúdo
    val genre: String? = null,
    val genresJson: String = "[]",
    val tagsJson: String = "[]",
    val durationMs: Long? = null,
    
    // Timestamp do evento
    val listenedAt: Long = System.currentTimeMillis(),
    val hourOfDay: Int = 0,
    val dayOfWeek: Int = 0,
    val dateString: String = "",
    
    // Tipo de interação
    val interactionType: InteractionType = InteractionType.PLAYED,
    
    // Quanto tempo foi ouvido (em ms)
    val listenedDurationMs: Long = 0,
    
    // Porcentagem ouvida
    val listenedPercentage: Float = 0f,
    
    // Fonte (qual tela/app iniciou)
    val source: String = "metal_tab",
    
    // Completion status
    val completed: Boolean = false
) {
    init {
        // Preencher campos derivados do timestamp
        val timestamp = listenedAt
        val javaDate = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        hourOfDay
        dayOfWeek
    }
    
    companion object {
        fun create(
            artistName: String,
            albumName: String? = null,
            trackName: String? = null,
            genre: String? = null,
            durationMs: Long? = null,
            interactionType: InteractionType = InteractionType.PLAYED
        ): ListeningHistoryEntity {
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = now
            }
            
            return ListeningHistoryEntity(
                artistName = artistName,
                albumName = albumName,
                trackName = trackName,
                genre = genre,
                durationMs = durationMs,
                listenedAt = now,
                hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK),
                dateString = "${calendar.get(java.util.Calendar.YEAR)}-${(calendar.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}-${calendar.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}",
                interactionType = interactionType
            )
        }
    }
    
    fun isLongListen(): Boolean {
        return listenedDurationMs >= 180000 // 3+ minutos
    }
    
    fun isShortListen(): Boolean {
        return listenedDurationMs < 30000 // < 30 segundos
    }
    
    fun isCompleteListen(): Boolean {
        return completed || (durationMs != null && listenedPercentage > 0.85f)
    }
}

enum class InteractionType {
    PLAYED,       // Reprodução iniciada
    COMPLETED,    // Reprodução completada (>85%)
    SKIPPED,      // Pulado antes de 30s
    PAUSED,       // Pausado
    RESUMED,      // Continuado
    FAVORITED,   // Marcado como favorito
    UNFAVORITED, // Desmarcado favorito
    REPLAYED,    // Reproduzido novamente
    SHARED       // Compartilhado
}

/**
 * Agregação de histórico para análise de padrões
 */
data class ListeningStats(
    val artistName: String,
    val totalPlays: Int,
    val totalListenTimeMs: Long,
    val avgListenDurationMs: Long,
    val completionRate: Float,
    val skipRate: Float,
    val lastPlayedAt: Long,
    val favoriteTimeOfDay: String = "",
    val favoriteDayOfWeek: String = "",
    val firstPlayedAt: Long = 0
)

/**
 * DTO específico para query de estatísticas de artista do Room
 * Não reutilizar ListeningStats - Room exige correspondência exata de colunas
 */
data class ArtistStatsDto(
    val artistName: String,
    val totalPlays: Int,
    val totalListenTimeMs: Long,
    val avgListenDurationMs: Long,
    val completionRate: Float,
    val skipRate: Float,
    val lastPlayedAt: Long
)

/**
 * Perfil musical do usuário baseado em histórico
 */
data class MusicProfile(
    val dominantGenres: List<GenreStats>,
    val dominantCountries: List<CountryStats>,
    val favoriteArtists: List<ArtistStats>,
    val listeningPatterns: ListeningPatternStats,
    val weeklyEvolution: Map<String, Int>,
    val totalListenTimeMs: Long,
    val totalTracksPlayed: Int,
    val uniqueArtistsCount: Int,
    val uniqueAlbumsCount: Int,
    val generatedAt: Long = System.currentTimeMillis()
)

data class GenreStats(
    val genre: String,
    val playCount: Int,
    val totalListenTimeMs: Long,
    val percentage: Float
)

data class CountryStats(
    val country: String,
    val countryCode: String?,
    val playCount: Int,
    val percentage: Float
)

data class ArtistStats(
    val artistMbid: String?,
    val artistName: String,
    val playCount: Int,
    val totalListenTimeMs: Long,
    val completionRate: Float,
    val avgRating: Float?
)

data class ListeningPatternStats(
    val mostActiveHour: Int,
    val mostActiveDay: Int,
    val avgSessionDurationMs: Long,
    val avgTracksPerSession: Int,
    val preferredSessionLength: String
)