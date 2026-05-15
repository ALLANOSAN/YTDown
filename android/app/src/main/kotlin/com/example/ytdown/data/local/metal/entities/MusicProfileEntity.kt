package com.example.ytdown.data.local.metal.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade de Perfil Musical - Sistema Metal
 * 
 * Armazena o perfil musical calculado do usuário baseado em:
 * - Histórico de escuta
 * - Gêneros predominantes
 * - Artistas favoritos
 * - Padrões de horário
 * 
 * O perfil é recalculado periodicamente para manter relevância.
 */
@Entity(
    tableName = "music_profile",
    indices = [
        androidx.room.Index(value = ["generatedAt"]),
        androidx.room.Index(value = ["isActive"])
    ]
)
data class MusicProfileEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton - apenas um perfil por usuário
    
    // Gêneros dominantes (JSON array)
    val dominantGenresJson: String = "[]",
    
    // Tags dominantes
    val dominantTagsJson: String = "[]",
    
    // Países predominantes
    val dominantCountriesJson: String = "[]",
    
    // Artistas favoritos
    val favoriteArtistsJson: String = "[]",
    
    // Estatísticas gerais
    val totalListenTimeMs: Long = 0,
    val totalTracksPlayed: Int = 0,
    val totalAlbumsPlayed: Int = 0,
    val uniqueArtistsCount: Int = 0,
    val uniqueAlbumsCount: Int = 0,
    
    // Padrões de escuta
    val mostActiveHour: Int = 0,
    val mostActiveDay: Int = 0,
    val avgSessionDurationMs: Long = 0,
    val avgTracksPerSession: Int = 0,
    
    // Evolução semanal (dia -> quantidade de plays)
    val weeklyEvolutionJson: String = "{}",
    
    // Score musical (0-100)
    val musicScore: Double = 0.0,
    
    // Nível de descobridor (quão aberto a descobrir novos estilos)
    val discoveryScore: Double = 0.0,
    
    // Timestamp de geração
    val generatedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    
    // Status
    val isActive: Boolean = true,
    val calculationVersion: Int = 1
) {
    fun getDominantGenres(): List<GenreStats> {
        return parseJsonList(dominantGenresJson) { map ->
            GenreStats(
                genre = map["genre"] ?: "",
                playCount = map["playCount"]?.toIntOrNull() ?: 0,
                totalListenTimeMs = map["totalListenTimeMs"]?.toLongOrNull() ?: 0,
                percentage = map["percentage"]?.toFloatOrNull() ?: 0f
            )
        }
    }
    
    fun getFavoriteArtists(): List<ArtistStats> {
        return parseJsonList(favoriteArtistsJson) { map ->
            ArtistStats(
                artistMbid = map["artistMbid"],
                artistName = map["artistName"] ?: "",
                playCount = map["playCount"]?.toIntOrNull() ?: 0,
                totalListenTimeMs = map["totalListenTimeMs"]?.toLongOrNull() ?: 0,
                completionRate = map["completionRate"]?.toFloatOrNull() ?: 0f,
                avgRating = map["avgRating"]?.toFloatOrNull()
            )
        }
    }
    
    fun getWeeklyEvolution(): Map<String, Int> {
        return try {
            weeklyEvolutionJson
                .removeSurrounding("{", "}")
                .split(",")
                .associate { pair ->
                    val (key, value) = pair.split(":")
                    key.trim().removeSurrounding("\"") to (value.trim().toIntOrNull() ?: 0)
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private inline fun <T> parseJsonList(json: String, transform: (Map<String, String>) -> T): List<T> {
        return try {
            val cleanJson = json.removeSurrounding("[", "]")
            if (cleanJson.isBlank()) return emptyList()
            
            cleanJson
                .split("},{")
                .map { item ->
                    val clean = item.removeSurrounding("{", "}")
                    val map = clean.split(",")
                        .associate { pair ->
                            val (key, value) = pair.split(":")
                            key.trim().removeSurrounding("\"") to value.trim().removeSurrounding("\"")
                        }
                    transform(map)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    companion object {
        fun create(
            genreStats: List<GenreStats>,
            artistStats: List<ArtistStats>,
            patternStats: ListeningPatternStats,
            weeklyEvolution: Map<String, Int>,
            totalListenTime: Long,
            totalTracks: Int,
            uniqueArtists: Int,
            uniqueAlbums: Int
        ): MusicProfileEntity {
            return MusicProfileEntity(
                dominantGenresJson = genreStats.toJsonString(),
                favoriteArtistsJson = artistStats.take(20).toJsonList(),
                totalListenTimeMs = totalListenTime,
                totalTracksPlayed = totalTracks,
                uniqueArtistsCount = uniqueArtists,
                uniqueAlbumsCount = uniqueAlbums,
                mostActiveHour = patternStats.mostActiveHour,
                mostActiveDay = patternStats.mostActiveDay,
                avgSessionDurationMs = patternStats.avgSessionDurationMs,
                avgTracksPerSession = patternStats.avgTracksPerSession,
                weeklyEvolutionJson = weeklyEvolution.toJsonMap(),
                musicScore = calculateMusicScore(genreStats, artistStats),
                discoveryScore = calculateDiscoveryScore(artistStats)
            )
        }
        
        private fun calculateMusicScore(genres: List<GenreStats>, artists: List<ArtistStats>): Double {
            // Score baseado em consistência de escuta
            val genreConsistency = genres.take(3).sumOf { it.percentage.toDouble() }
            val artistEngagement = artists.take(10).sumOf { it.playCount.toDouble() }.coerceAtMost(1000.0) / 10.0
            
            return ((genreConsistency * 0.4 + artistEngagement * 0.6) / 100.0 * 100).coerceIn(0.0, 100.0)
        }
        
        private fun calculateDiscoveryScore(artists: List<ArtistStats>): Double {
            // Score baseado em variety de artistas
            val variety = artists.size.coerceAtMost(50) / 50.0
            return (variety * 100).coerceIn(0.0, 100.0)
        }
        
        private fun List<GenreStats>.toJsonString(): String {
            if (isEmpty()) return "[]"
            return "[${joinToString(",") { g ->
                """{"genre":"${g.genre}","playCount":${g.playCount},"totalListenTimeMs":${g.totalListenTimeMs},"percentage":${g.percentage}}"""
            }}]"
        }
        
        private fun List<ArtistStats>.toJsonList(): String {
            if (isEmpty()) return "[]"
            return "[${joinToString(",") { a ->
                """{"artistMbid":"${a.artistMbid ?: ""}","artistName":"${a.artistName}","playCount":${a.playCount},"totalListenTimeMs":${a.totalListenTimeMs},"completionRate":${a.completionRate},"avgRating":${a.avgRating ?: "null"}}"""
            }}]"
        }
        
        private fun Map<String, Int>.toJsonMap(): String {
            if (isEmpty()) return "{}"
            val builder = StringBuilder("{")
            forEach { (k, v) ->
                if (builder.length > 1) builder.append(",")
                builder.append("\"$k\": $v")
            }
            builder.append("}")
            return builder.toString()
        }
    }
}