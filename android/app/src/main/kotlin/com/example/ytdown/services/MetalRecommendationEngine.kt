package com.example.ytdown.services

import com.example.ytdown.core.domain.DownloadItemEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sistema de Recomendação de Metal - Algoritmo local para descoberta de bandas
 * 
 * Este serviço analisa o perfil musical do usuário e calcula scores de similaridade
 * para recomendar bandas semelhantes baseadas em:
 * - Tags/gêneros
 * - Subgêneros de metal
 * - País de origem
 * - Palavras-chave do estilo
 * - Score de compatibilidade
 */
@Singleton
class MetalRecommendationEngine @Inject constructor() {

    // =====================================================
    // SUBGÊNEROS DE METAL - Keywords para detecção
    // =====================================================

    private val subgenreKeywords = mapOf(
        "white metal" to listOf("white metal", "christian metal", "christian power metal"),
        "power metal" to listOf("power metal", "euro power", "us power"),
        "black metal" to listOf("black metal", "atmospheric black metal", "symphonic black metal", "raw black metal"),
        "death metal" to listOf("death metal", "melodic death metal", "technical death metal", "slam death"),
        "thrash metal" to listOf("thrash metal", "speed metal", "crossover thrash"),
        "doom metal" to listOf("doom metal", "traditional doom", "drone", "stoner metal"),
        "symphonic metal" to listOf("symphonic metal", "symphonic black metal", "symphonic death"),
        "gothic metal" to listOf("gothic metal", "gothic rock", "darkwave"),
        "progressive metal" to listOf("progressive metal", "technical metal", "prog metal", "math metal"),
        "metalcore" to listOf("metalcore", "melodic metalcore", "deathcore"),
        "heavy metal" to listOf("heavy metal", "traditional metal", "new wave of british metal"),
        "hair metal" to listOf("glam metal", "hair metal", "sleaze rock"),
        "industrial metal" to listOf("industrial metal", "industrial rock", "aggro industrial"),
        "avant-garde metal" to listOf("avant-garde metal", "experimental metal", "extreme metal"),
        "neoclassical metal" to listOf("neoclassical metal", "shred", "instrumental metal")
    )

    private val metalTagSet = setOf(
        "black metal", "death metal", "power metal", "thrash metal",
        "heavy metal", "doom metal", "symphonic metal", "gothic metal",
        "progressive metal", "metalcore", "deathcore", "industrial metal",
        "avant-garde metal", "speed metal", "traditional metal", "hair metal",
        "neoclassical metal", "melodic death metal", "technical death metal",
        "atmospheric black metal", "christian metal", "white metal"
    )

    // =====================================================
    // PERFIL MUSICAL DO USUÁRIO
    // =====================================================

    /**
     * Analisa a biblioteca do usuário e cria um perfil musical
     * @param libraryItems Lista de músicas na biblioteca
     * @return Perfil musical do usuário com gêneros, tags, bandas e países favoritos
     */
    fun analyzeUserProfile(libraryItems: List<DownloadItemEntity>): UserMetalProfile {
        val genreFrequency = mutableMapOf<String, Int>()
        val tagFrequency = mutableMapOf<String, Int>()
        val bandFrequency = mutableMapOf<String, Int>()
        val countryFrequency = mutableMapOf<String, Int>()

        // Analisar cada item da biblioteca — baseado APENAS no nome da banda
        libraryItems.forEach { item ->
            item.artist?.toString()?.let { artist ->
                if (artist.isNotBlank()) {
                    bandFrequency[artist.lowercase()] = (bandFrequency[artist.lowercase()] ?: 0) + 1
                }
            }
        }

        val sortedBands = bandFrequency.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }

        return UserMetalProfile(
            favoriteGenres = emptyList(),   // preenchido via MusicBrainz no Repository
            favoriteTags = emptyList(),     // preenchido via MusicBrainz no Repository
            favoriteBands = sortedBands,
            favoriteCountries = emptyList(),
            totalTracks = libraryItems.size,
            analysisTimestamp = System.currentTimeMillis()
        )
    }

    // =====================================================
    // SCORE DE SIMILARIDADE
    // =====================================================

    /**
     * Calcula o score de similaridade entre um artista e o perfil do usuário
     * @param artistTags Tags do artista (obtidas do MusicBrainz)
     * @param artistGenre Gênero principal do artista
     * @param artistCountry País do artista
     * @param userProfile Perfil do usuário
     * @return Score de similaridade (0-100)
     */
    fun calculateSimilarityScore(
        artistTags: List<String>,
        artistGenre: String?,
        artistCountry: String?,
        userProfile: UserMetalProfile
    ): SimilarityResult {
        var totalScore = 0
        val matchedTags = mutableListOf<String>()
        val matchReasons = mutableListOf<String>()

        // 1. Score por tags em comum (peso: 40%)
        val userTagSet = userProfile.favoriteTags.map { it.lowercase() }.toSet()
        artistTags.forEach { tag ->
            val tagLower = tag.lowercase()
            if (tagLower in userTagSet || userTagSet.any { userTag -> 
                tagLower.contains(userTag) || userTag.contains(tagLower) 
            }) {
                totalScore += 15
                matchedTags.add(tag)
            } else if (metalTagSet.contains(tagLower) && userProfile.favoriteTags.any {
                    it.lowercase().contains(extractGenreCore(tagLower))
                }) {
                // Tag de metal que corresponde ao estilo geral
                totalScore += 10
            }
        }

        // 2. Score por gênero (peso: 25%)
        if (artistGenre != null) {
            val artistGenreLower = artistGenre.lowercase()
            userProfile.favoriteGenres.forEach { genre ->
                if (artistGenreLower.contains(genre) || genre.contains(artistGenreLower)) {
                    totalScore += 20
                    matchReasons.add("Gênero: $genre")
                    return@forEach
                }
            }
        }

        // 3. Score por país (peso: 10%)
        if (artistCountry != null && userProfile.favoriteCountries.isNotEmpty()) {
            if (userProfile.favoriteCountries.any { it.equals(artistCountry, ignoreCase = true) }) {
                totalScore += 10
                matchReasons.add("País: $artistCountry")
            }
        }

        // 4. Bonus por bandas base (peso: 15%)
        val userBandSet = userProfile.favoriteBands.map { it.lowercase() }.toSet()
        artistTags.forEach { tag ->
            if (userBandSet.any { band -> tag.lowercase().contains(band) || band.contains(tag.lowercase()) }) {
                totalScore += 5
            }
        }

        // Normalizar para escala 0-100
        val finalScore = (totalScore * 100 / 85).coerceIn(0, 100)

        return SimilarityResult(
            score = finalScore,
            matchedTags = matchedTags.take(5),
            matchReasons = matchReasons,
            confidence = calculateConfidence(finalScore, userProfile.totalTracks)
        )
    }

    /**
     * Detecta o subgênero predominante nas tags
     */
    fun detectPredominantGenre(tags: List<String>): String? {
        val tagCounts = mutableMapOf<String, Int>()
        
        tags.forEach { tag ->
            val tagLower = tag.lowercase()
            subgenreKeywords.forEach { (genre, keywords) ->
                keywords.forEach { keyword ->
                    if (tagLower.contains(keyword)) {
                        tagCounts[genre] = (tagCounts[genre] ?: 0) + 1
                    }
                }
            }
        }

        return tagCounts.maxByOrNull { it.value }?.key
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private fun detectGenreFromAlbum(album: String): String? {
        val albumLower = album.lowercase()
        subgenreKeywords.forEach { (genre, keywords) ->
            keywords.forEach { keyword ->
                if (albumLower.contains(keyword)) {
                    return genre
                }
            }
        }
        return null
    }

    private fun extractCountryHint(album: String): String {
        // Tentativas simples de detectar país pelo nome do álbum/artista
        return when {
            album.contains("Finnish", ignoreCase = true) -> "FI"
            album.contains("Swedish", ignoreCase = true) -> "SE"
            album.contains("American", ignoreCase = true) -> "US"
            album.contains("German", ignoreCase = true) -> "DE"
            album.contains("Brazilian", ignoreCase = true) -> "BR"
            album.contains("Japanese", ignoreCase = true) -> "JP"
            else -> "Unknown"
        }
    }

    private fun extractGenreCore(tag: String): String {
        return tag.replace(Regex("(metal|rock|music)$"), "").trim()
    }

    private fun calculateConfidence(score: Int, totalTracks: Int): Float {
        val trackFactor = when {
            totalTracks > 100 -> 1.0f
            totalTracks > 50 -> 0.8f
            totalTracks > 20 -> 0.6f
            else -> 0.4f
        }
        val scoreFactor = score / 100f
        return (trackFactor * scoreFactor).coerceIn(0f, 1f)
    }
}

// =====================================================
// DATA MODELS
// =====================================================

/**
 * Perfil musical do usuário focado em metal
 */
data class UserMetalProfile(
    val favoriteGenres: List<String>,
    val favoriteTags: List<String>,
    val favoriteBands: List<String>,
    val favoriteCountries: List<String>,
    val totalTracks: Int = 0,
    val analysisTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Retorna o gênero predominante do usuário
     */
    fun getPredominantGenre(): String? = favoriteGenres.firstOrNull()

    /**
     * Retorna as principais tags para busca
     */
    fun getTopSearchTags(): List<String> = favoriteTags.take(6)

    /**
     * Verifica se o perfil está bem definido (mínimo de dados)
     */
    fun isWellDefined(): Boolean = totalTracks >= 5 && (favoriteGenres.isNotEmpty() || favoriteTags.isNotEmpty())

    /**
     * Retorna descrição do perfil para UI
     */
    fun getProfileDescription(): String {
        val genre = getPredominantGenre() ?: "Metal"
        val bandCount = favoriteBands.size
        return "$genre • $bandCount bandas detectadas • $totalTracks músicas"
    }
}

/**
 * Resultado da análise de similaridade
 */
data class SimilarityResult(
    val score: Int,                    // 0-100
    val matchedTags: List<String>,     // Tags que coincidem com o perfil
    val matchReasons: List<String>,    // Reasons de matching (gênero, país, etc)
    val confidence: Float              // 0.0-1.0 confiança na recomendação
) {
    /**
     * Verifica se a recomendação é de alta qualidade
     */
    fun isHighQuality(): Boolean = score >= 30 && confidence >= 0.5f

    /**
     * Retorna nível do match para exibição
     */
    fun getMatchLevel(): MatchLevel = when {
        score >= 70 && confidence >= 0.7f -> MatchLevel.EXCELLENT
        score >= 50 && confidence >= 0.5f -> MatchLevel.GOOD
        score >= 30 && confidence >= 0.4f -> MatchLevel.MEDIUM
        else -> MatchLevel.LOW
    }
}

enum class MatchLevel {
    EXCELLENT,  // ★★★★★ - Alta similaridade
    GOOD,       // ★★★★☆ - Boa similaridade
    MEDIUM,     // ★★★☆☆ - Similaridade moderada
    LOW         // ★★☆☆☆ - Baixa similaridade
}