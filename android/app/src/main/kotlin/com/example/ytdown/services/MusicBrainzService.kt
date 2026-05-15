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
 * MusicBrainz Service - Implementação completa da API
 * 
 * Documentação oficial: https://musicbrainz.org/doc/MusicBrainz_API
 * 
 * Rate Limit: 1 request por segundo (OBRIGATÓRIO)
 * User-Agent: Obrigatório para identificação
 * 
 * Endpoints utilizados:
 * - /ws/2/artist - Buscar artistas
 * - /ws/2/artist/{mbid} - Detalhes do artista
 * - /ws/2/release-group - Grupos de lançamentos (álbuns)
 * - /ws/2/release - Lançamentos específicos
 * - /ws/2/tag - Tags e gêneros
 * - /ws/2/area - Países e regiões
 */
@Singleton
class MusicBrainzService @Inject constructor() {

    companion object {
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"
        private const val REQUEST_DELAY_MS = 1100L
        private const val CONNECT_TIMEOUT = 12000
        private const val READ_TIMEOUT = 12000
    }

    // =====================================================
    // ARTIST SEARCH - Buscar artistas
    // =====================================================
    
    /**
     * Busca artistas por nome
     * @param query Nome do artista
     * @param limit Número máximo de resultados
     * @return Lista de artistas encontrados
     */
    suspend fun searchArtists(query: String, limit: Int = 10): List<MBArtist> = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/artist?query=$encodedQuery&limit=$limit&fmt=json"
            val json = fetchJson(url) ?: return@withContext emptyList()
            
            val artistsArray = json.optJSONArray("artists") ?: return@withContext emptyList()
            val artists = mutableListOf<MBArtist>()
            
            for (i in 0 until artistsArray.length()) {
                val artist = artistsArray.getJSONObject(i)
                artists.add(parseArtist(artist))
            }
            
            artists
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca artistas por tag (gênero musical)
     * @param tag Tag/gênero (ex: "power metal", "black metal")
     * @param limit Número máximo de resultados
     * @return Lista de artistas filtrados por tag
     */
    suspend fun searchArtistsByTag(tag: String, limit: Int = 20): List<MBArtist> = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val encodedTag = URLEncoder.encode("tag:\"$tag\"", "UTF-8")
            val url = "$BASE/artist?query=$encodedTag&limit=$limit&fmt=json"
            val json = fetchJson(url) ?: return@withContext emptyList()
            
            val artistsArray = json.optJSONArray("artists") ?: return@withContext emptyList()
            val artists = mutableListOf<MBArtist>()
            
            for (i in 0 until artistsArray.length()) {
                val artist = artistsArray.getJSONObject(i)
                artists.add(parseArtist(artist))
            }
            
            artists
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =====================================================
    // ARTIST DETAILS - Detalhes do artista
    // =====================================================

    /**
     * Obtém detalhes completos de um artista
     * @param mbid MusicBrainz ID do artista
     * @param inc Recursos adicionais (tags, relaciones, etc)
     * @return Dados completos do artista ou null
     */
    suspend fun getArtistDetails(mbid: String, inc: String = "tags,artist-rels,url-rels,alias"): MBBandDetails? = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = "$BASE/artist/$mbid?inc=$inc&fmt=json"
            val json = fetchJson(url) ?: return@withContext null
            
            parseArtistDetails(json)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Busca artista por nome e retorna MBID
     */
    suspend fun searchArtistId(name: String): String? = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val q = URLEncoder.encode(name, "UTF-8")
            val json = fetchJson("$BASE/artist?query=$q&limit=1&fmt=json") ?: return@withContext null
            val artists = json.optJSONArray("artists") ?: return@withContext null
            if (artists.length() == 0) return@withContext null
            artists.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // =====================================================
    // RELEASE GROUPS - Álbuns/Discos
    // =====================================================

    /**
     * Busca release groups (álbuns) de um artista
     * @param artistMBID MBID do artista
     * @param type Tipo de lançamento (album, compilation, etc)
     * @param limit Número máximo de resultados
     * @return Lista de álbuns
     */
    suspend fun getArtistReleaseGroups(
        artistMBID: String,
        type: String = "album|studio",
        limit: Int = 50
    ): List<MBReleaseGroup> = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = "$BASE/release-group?artist=$artistMBID&type=$type&limit=$limit&fmt=json"
            val json = fetchJson(url) ?: return@withContext emptyList()
            
            val releaseGroups = json.optJSONArray("release-groups") ?: return@withContext emptyList()
            val groups = mutableListOf<MBReleaseGroup>()
            
            for (i in 0 until releaseGroups.length()) {
                val rg = releaseGroups.getJSONObject(i)
                groups.add(
                    MBReleaseGroup(
                        id = rg.optString("id"),
                        title = rg.optString("title"),
                        firstReleaseDate = rg.optString("first-release-date", ""),
                        primaryType = rg.optString("primary-type", ""),
                        secondaryTypes = rg.optJSONArray("secondary-types")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        artistCredit = rg.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name") ?: ""
                    )
                )
            }
            
            groups
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =====================================================
    // RELEASES - Lançamentos específicos
    // =====================================================

    /**
     * Busca releases (versões específicas) de um release group
     * @param releaseGroupMBID MBID do release group
     * @return Lista de releases
     */
    suspend fun getReleases(releaseGroupMBID: String): List<MBRelease> = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = "$BASE/release?release-group=$releaseGroupMBID&limit=10&fmt=json"
            val json = fetchJson(url) ?: return@withContext emptyList()
            
            val releases = json.optJSONArray("releases") ?: return@withContext emptyList()
            val results = mutableListOf<MBRelease>()
            
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                results.add(
                    MBRelease(
                        id = release.optString("id"),
                        title = release.optString("title"),
                        date = release.optString("date", ""),
                        country = release.optString("country", ""),
                        format = release.optJSONArray("formats")?.optJSONObject(0)?.optString("name") ?: ""
                    )
                )
            }
            
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =====================================================
    // TAGS - Gêneros e estilos
    // =====================================================

    /**
     * Busca as tags de um artista
     * @param artistName Nome do artista
     * @return Lista de tags ordenadas por relevância
     */
    suspend fun getArtistTags(artistName: String): List<TagEntry> = withContext(Dispatchers.IO) {
        try {
            val artistId = searchArtistId(artistName) ?: return@withContext emptyList()
            val artist = lookupArtist(artistId, inc = "tags") ?: return@withContext emptyList()
            val tagsArray = artist.optJSONArray("tags") ?: return@withContext emptyList()

            val tags = mutableListOf<TagEntry>()
            for (i in 0 until tagsArray.length()) {
                val tagObj = tagsArray.getJSONObject(i)
                tags.add(TagEntry(
                    name = tagObj.optString("name"),
                    count = tagObj.optInt("count", 0)
                ))
            }
            tags.sortedByDescending { it.count }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca todas as tags populares de metal
     */
    suspend fun getPopularMetalTags(): List<String> = withContext(Dispatchers.IO) {
        listOf(
            "black metal", "death metal", "power metal", "thrash metal",
            "heavy metal", "doom metal", "symphonic metal", "gothic metal",
            "progressive metal", "metalcore", "deathcore", "blackened death metal",
            "melodic death metal", "traditional metal", "speed metal", "industrial metal",
            "neoclassical metal", "christian metal", "white metal", "hair metal",
            "slam death metal", "technical death metal", "avant-garde metal"
        )
    }

    // =====================================================
    // SIMILAR ARTISTS - Artistas semelhantes
    // =====================================================

    /**
     * Descobre artistas semelhantes usando relaciones do MusicBrainz
     * @param bandName Nome da banda
     * @return Lista de bandas similares
     */
    suspend fun discoverSimilarBands(bandName: String): MBDiscoveryResponse = withContext(Dispatchers.IO) {
        try {
            val artistId = searchArtistId(bandName) ?: return@withContext MBDiscoveryResponse(
                success = false,
                error = "Artista '$bandName' não encontrado no MusicBrainz."
            )

            val artistDetails = lookupArtist(artistId, inc = "tags,artist-rels")
                ?: return@withContext MBDiscoveryResponse(
                    success = false,
                    error = "Não foi possível buscar detalhes do artista."
                )

            val relations = artistDetails.optJSONArray("relations") ?: JSONArray()
            val similarBands = mutableListOf<MBBand>()

            for (i in 0 until relations.length()) {
                val rel = relations.getJSONObject(i)
                if (rel.optString("type") == "similar to") {
                    val target = rel.optJSONObject("target")?.optJSONObject("artist") ?: continue
                    val name = target.optString("name")
                    if (name.isNotBlank()) {
                        similarBands.add(MBBand(
                            name = name,
                            mbid = target.optString("id"),
                            genre = parseGenreFromTags(artistDetails),
                            country = target.optString("country").takeIf { it.isNotBlank() },
                            tags = emptyList()
                        ))
                    }
                }
            }

            if (similarBands.isEmpty()) {
                return@withContext MBDiscoveryResponse(
                    success = false,
                    error = "Nenhuma banda similar encontrada."
                )
            }

            MBDiscoveryResponse(success = true, bands = similarBands.take(20))
        } catch (e: Exception) {
            MBDiscoveryResponse(success = false, error = "Erro: ${e.message}")
        }
    }

    // =====================================================
    // AREAS - Países e regiões
    // =====================================================

    /**
     * Busca informações de país/região
     */
    suspend fun getArea(areaId: String): MBArea? = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = "$BASE/area/$areaId?fmt=json"
            val json = fetchJson(url) ?: return@withContext null
            
            MBArea(
                id = json.optString("id"),
                name = json.optString("name"),
                type = json.optString("type", ""),
                countryCode = parseCountryCode(json.optString("iso-3166-1-codes"))
            )
        } catch (e: Exception) {
            null
        }
    }

// =====================================================
    // PRIVATE HELPERS - Métodos auxiliares
    // =====================================================
    
    /**
     * Parseia código de país do campo iso-3166-1-codes
     * O campo pode vir como string "[\"US\"]" ou como JSONArray
     */
    private fun parseCountryCode(countryField: String?): String? {
        if (countryField.isNullOrBlank()) return null
        
        return try {
            // Tentar parsear como JSONArray primeiro
            val jsonArray = JSONArray(countryField)
            if (jsonArray.length() > 0) {
                jsonArray.getString(0)
            } else {
                null
            }
        } catch (e: Exception) {
            // Se falhar, pode ser uma string simples entre aspas
            val trimmed = countryField.trim()
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed.removeSurrounding("\"")
            } else {
                trimmed
            }.takeIf { it.isNotBlank() }
        }
    }
    
    private suspend fun lookupArtist(mbid: String, inc: String): JSONObject? {
        delay(REQUEST_DELAY_MS)
        return fetchJson("$BASE/artist/$mbid?inc=$inc&fmt=json")
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            if (conn.responseCode == 429) return null
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseArtist(json: JSONObject): MBArtist {
        val tags = mutableListOf<String>()
        val tagsArray = json.optJSONArray("tags")
        if (tagsArray != null) {
            for (j in 0 until tagsArray.length()) {
                tags.add(tagsArray.getJSONObject(j).optString("name"))
            }
        }
        
        return MBArtist(
            id = json.optString("id"),
            name = json.optString("name"),
            sortName = json.optString("sort-name", ""),
            country = json.optString("country", ""),
            type = json.optString("type", ""),
            beginArea = json.optJSONObject("begin-area")?.optString("name"),
            endArea = json.optJSONObject("end-area")?.optString("name"),
            lifeSpan = json.optJSONObject("life-span")?.let { ls ->
                LifeSpan(
                    begin = ls.optString("begin", ""),
                    end = ls.optString("end", ""),
                    ended = ls.optBoolean("ended", false)
                )
            },
            tags = tags,
            score = json.optInt("score", 0)
        )
    }

    private fun parseArtistDetails(json: JSONObject): MBBandDetails {
        val tags = mutableListOf<String>()
        val tagsArray = json.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getJSONObject(i).optString("name"))
            }
        }
        
        val genres = mutableListOf<String>()
        val genresArray = json.optJSONArray("genres")
        if (genresArray != null) {
            for (i in 0 until genresArray.length()) {
                genres.add(genresArray.getJSONObject(i).optString("name"))
            }
        }
        
        return MBBandDetails(
            id = json.optString("id"),
            name = json.optString("name"),
            sortName = json.optString("sort-name", ""),
            country = json.optString("country", ""),
            type = json.optString("type", ""),
            beginArea = json.optJSONObject("begin-area")?.optString("name"),
            disambiguation = json.optString("disambiguation", ""),
            tags = tags,
            genres = genres,
            aliases = json.optJSONArray("aliases")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }
            } ?: emptyList(),
            urls = parseUrls(json)
        )
    }

    private fun parseUrls(artist: JSONObject): List<MBUrl> {
        val urls = mutableListOf<MBUrl>()
        val relations = artist.optJSONArray("relations") ?: return urls
        
        for (i in 0 until relations.length()) {
            val rel = relations.getJSONObject(i)
            val urlType = rel.optString("type")
            if (urlType == "official homepage" || urlType == "wikipedia" || 
                urlType == "discogs" || urlType == "allmusic" || urlType == "last.fm") {
                val target = rel.optJSONObject("target")
                if (target != null) {
                    urls.add(MBUrl(
                        type = urlType,
                        url = target.optString("resource")
                    ))
                }
            }
        }
        
        return urls
    }

    private fun parseGenreFromTags(artist: JSONObject): String? {
        val genres = artist.optJSONArray("genres")
        if (genres != null && genres.length() > 0) {
            return genres.getJSONObject(0).optString("name")
        }
        val tags = artist.optJSONArray("tags")
        if (tags != null && tags.length() > 0) {
            val ignored = setOf("rock", "pop", "band", "group", "album", "artist", "electronic", "instrumental", "classic rock")
            for (i in 0 until tags.length()) {
                val tag = tags.getJSONObject(i).optString("name")
                if (tag.isNotBlank() && tag.lowercase() !in ignored) {
                    return tag.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return null
    }
}

// =====================================================
// DATA MODELS - Modelos de dados
// =====================================================

/**
 * Artista básico do MusicBrainz
 */
data class MBArtist(
    val id: String,
    val name: String,
    val sortName: String,
    val country: String,
    val type: String,
    val beginArea: String?,
    val endArea: String?,
    val lifeSpan: LifeSpan?,
    val tags: List<String>,
    val score: Int
)

/**
 * Período de atividade do artista
 */
data class LifeSpan(
    val begin: String,
    val end: String,
    val ended: Boolean
)

/**
 * Detalhes completos do artista/banda
 */
data class MBBandDetails(
    val id: String,
    val name: String,
    val sortName: String,
    val country: String,
    val type: String,
    val beginArea: String?,
    val disambiguation: String,
    val tags: List<String>,
    val genres: List<String>,
    val aliases: List<String>,
    val urls: List<MBUrl>
)

/**
 * URL relacionada ao artista
 */
data class MBUrl(
    val type: String,
    val url: String
)

/**
 * Release group (álbum)
 */
data class MBReleaseGroup(
    val id: String,
    val title: String,
    val firstReleaseDate: String,
    val primaryType: String,
    val secondaryTypes: List<String>,
    val artistCredit: String
)

/**
 * Release (versão específica)
 */
data class MBRelease(
    val id: String,
    val title: String,
    val date: String,
    val country: String,
    val format: String
)

/**
 * Área/país
 */
data class MBArea(
    val id: String,
    val name: String,
    val type: String,
    val countryCode: String?
)

// Modelos existentes mantidos para compatibilidade

data class MBBand(
    val name: String,
    val mbid: String,
    val genre: String? = null,
    val country: String? = null,
    val tags: List<String> = emptyList()
)

data class MBAlbum(
    val id: String,
    val name: String,
    val year: String
)

data class MBDiscoveryResponse(
    val success: Boolean,
    val bands: List<MBBand> = emptyList(),
    val error: String? = null
)

data class MBDetailsResponse(
    val success: Boolean,
    val name: String? = null,
    val genre: String? = null,
    val image_url: String? = null,
    val error: String? = null
)

data class MBAlbumsResponse(
    val success: Boolean,
    val albums: List<MBAlbum> = emptyList(),
    val error: String? = null
)