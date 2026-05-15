package com.example.ytdown.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic Music Discovery Engine - Sistema de descoberta musical dinâmica
 * 
 * Este sistema analiza dinamicamente a biblioteca do usuário para descobrir
 * padrões e estilos musicais automaticamente, sem hardcoded de gêneros.
 * 
 * Funcionamento:
 * 1. Analisa a biblioteca do usuário para extrair artistas
 * 2. Para cada artista, busca tags no MusicBrainz
 * 3. Agrega as tags mais comuns da biblioteca
 * 4. Usa essas tags para encontrar novos artistas similares
 * 5. Calcula scores de similaridade baseados nas tags
 * 6. Descobre bandas automaticamente baseadas nos padrões da biblioteca
 */
@Singleton
class DynamicMusicDiscovery @Inject constructor(
    private val musicBrainzService: MusicBrainzService
) {
    companion object {
        private const val REQUEST_DELAY_MS = 1100L
    }

    /**
     * Analisa a biblioteca do usuário e cria um perfil dinâmico de música
     * baseado EXCLUSIVAMENTE nos dados reais da biblioteca e da API.
     * 
     * @param libraryArtists Lista de artistas únicos da biblioteca do usuário
     * @return Perfil musical dinâmico com estilos descobertos automaticamente
     */
    suspend fun analyzeLibraryAndDiscover(
        libraryArtists: List<String>
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        
        if (libraryArtists.isEmpty()) {
            return@withContext DiscoveryResult(
                success = false,
                error = "Biblioteca vazia. Adicione músicas para descobrir novos artistas.",
                detectedStyles = emptyList(),
                recommendedArtists = emptyList()
            )
        }

        try {
            // ========================================
            // PASSO 1: Análise de tags da biblioteca
            // ========================================
            
            // Obter tags de cada artista da biblioteca em paralelo
            val allTagsFromLibrary = mutableMapOf<String, Int>()
            
            // Limitar para evitar muitas requisições
            val artistsToAnalyze = libraryArtists.take(15)
            
            artistsToAnalyze.forEach { artistName ->
                try {
                    val tags = musicBrainzService.getArtistTags(artistName)
                    tags.forEach { tag ->
                        allTagsFromLibrary[tag.name.lowercase()] = 
                            (allTagsFromLibrary[tag.name.lowercase()] ?: 0) + tag.count
                    }
                    delay(REQUEST_DELAY_MS / 2)  // Delay menor para biblioteca local
                } catch (e: Exception) {
                    // Continuar mesmo se falhar
                }
            }

            // Ordenar tags por frequência
            val sortedTags = allTagsFromLibrary.entries
                .sortedByDescending { it.value }
                .take(20)
                .map { it.key }
                .toList()

            // ========================================
            // PASSO 2: Detectar estilos predominantes
            // ========================================
            
            // Os estilos são descobertos automaticamente das tags mais frequentes
            // Não há lista hardcoded - emerge da análise
            val detectedStyles = sortedTags.take(8).map { tag ->
                DiscoveredStyle(
                    name = tag.replaceFirstChar { it.uppercase() },
                    frequency = allTagsFromLibrary[tag] ?: 0,
                    source = "musicbrainz_tags"
                )
            }

            // ========================================
            // PASSO 3: Buscar novos artistas usando as tags descobertas
            // ========================================
            
            val discoveredArtists = mutableListOf<DiscoveredArtist>()
            val processedNames = mutableSetOf<String>()

            // Usar as top tags para buscar novos artistas
            for (tag in sortedTags.take(6)) {
                if (discoveredArtists.size >= 40) break
                
                try {
                    val searchResults = musicBrainzService.searchArtistsByTag(tag, limit = 15)
                    
                    searchResults.forEach { artist ->
                        val nameLower = artist.name.lowercase()
                        
                        // Evitar artistas já na biblioteca
                        if (nameLower in libraryArtists.map { it.lowercase() }) return@forEach
                        
                        // Evitar duplicatas
                        if (nameLower in processedNames) return@forEach
                        processedNames.add(nameLower)
                        
                        // Calcular compatibilidade baseando-se nas tags em comum
                        val artistTagsSet = artist.tags.map { it.lowercase() }.toSet()
                        val commonTags = sortedTags.filter { it in artistTagsSet }
                        val matchScore = commonTags.size * 15 + artist.score / 10

                        if (matchScore > 10) {
                            discoveredArtists.add(
                                DiscoveredArtist(
                                    mbid = artist.id,
                                    name = artist.name,
                                    country = artist.country,
                                    tags = artist.tags,
                                    matchScore = matchScore,
                                    matchedTags = commonTags,
                                    isActive = artist.lifeSpan?.ended != true,
                                    beginYear = artist.lifeSpan?.begin,
                                    endYear = if (artist.lifeSpan?.ended == true) artist.lifeSpan?.end else null
                                )
                            )
                        }
                    }
                    
                    delay(REQUEST_DELAY_MS)
                    
                } catch (e: Exception) {
                    continue
                }
            }

            // Ordenar por score de compatibilidade
            val finalRecommendations = discoveredArtists
                .sortedByDescending { it.matchScore }
                .take(40)

            DiscoveryResult(
                success = true,
                detectedStyles = detectedStyles,
                recommendedArtists = finalRecommendations,
                analyzedArtists = artistsToAnalyze.size,
                totalTagsFound = sortedTags.size
            )

        } catch (e: Exception) {
            DiscoveryResult(
                success = false,
                error = "Erro na descoberta: ${e.message}",
                detectedStyles = emptyList(),
                recommendedArtists = emptyList()
            )
        }
    }

    /**
     * Descobre artistas similares usando apenas as tags da API
     * (sem referência à biblioteca local para novos usuários)
     */
    suspend fun discoverWithSeedStyles(
        seedTags: List<String>,
        excludeNames: Set<String> = emptySet()
    ): List<DiscoveredArtist> = withContext(Dispatchers.IO) {
        
        val results = mutableListOf<DiscoveredArtist>()
        val processedNames = mutableSetOf<String>()

        for (tag in seedTags.take(5)) {
            if (results.size >= 30) break
            
            try {
                val artists = musicBrainzService.searchArtistsByTag(tag, limit = 12)
                
                artists.forEach { artist ->
                    val nameLower = artist.name.lowercase()
                    
                    if (nameLower in excludeNames.map { it.lowercase() }) return@forEach
                    if (nameLower in processedNames) return@forEach
                    processedNames.add(nameLower)

                    val matchScore = artist.tags.count { tag in it.lowercase() } * 20 + artist.score / 10

                    if (matchScore > 5) {
                        results.add(
                            DiscoveredArtist(
                                mbid = artist.id,
                                name = artist.name,
                                country = artist.country,
                                tags = artist.tags,
                                matchScore = matchScore,
                                matchedTags = artist.tags.filter { tag in it.lowercase() },
                                isActive = artist.lifeSpan?.ended != true,
                                beginYear = artist.lifeSpan?.begin,
                                endYear = if (artist.lifeSpan?.ended == true) artist.lifeSpan?.end else null
                            )
                        )
                    }
                }
                
                delay(REQUEST_DELAY_MS)
                
            } catch (e: Exception) {
                continue
            }
        }

        results.sortedByDescending { it.matchScore }.take(30)
    }
}

/**
 * Resultado da descoberta dinâmica
 */
data class DiscoveryResult(
    val success: Boolean,
    val error: String? = null,
    val detectedStyles: List<DiscoveredStyle>,
    val recommendedArtists: List<DiscoveredArtist>,
    val analyzedArtists: Int = 0,
    val totalTagsFound: Int = 0
)

/**
 * Estilo musical descoberto automaticamente
 */
data class DiscoveredStyle(
    val name: String,
    val frequency: Int,
    val source: String  // "musicbrainz_tags", "library_analysis", etc.
)

/**
 * Artista descoberto com score de compatibilidade
 */
data class DiscoveredArtist(
    val mbid: String,
    val name: String,
    val country: String?,
    val tags: List<String>,
    val matchScore: Int,
    val matchedTags: List<String>,
    val isActive: Boolean,
    val beginYear: String?,
    val endYear: String?
) {
    val activeYears: String?
        get() = when {
            beginYear != null && endYear != null -> "$beginYear - $endYear"
            beginYear != null && isActive -> "$beginYear - presente"
            else -> null
        }
}