package com.example.ytdown.services

import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
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
class MusicBrainzService internal constructor(
    private val http: MusicBrainzHttpClient,
    private val io: CoroutineDispatcher,
) {

    @Inject
    constructor() : this(
        OkHttpMusicBrainzClient(userAgent = USER_AGENT),
        Dispatchers.IO,
    )

    /** Resultado da leitura de uma resposta de busca do MusicBrainz. */
    internal sealed interface SearchOutcome {
        data class Ok(val recordings: JSONArray) : SearchOutcome
        data object RateLimited : SearchOutcome
        data object Empty : SearchOutcome
    }

    /** Par (recording, release) escolhido como origem da faixa. */
    internal data class ReleaseChoice(
        val recording: JSONObject,
        val release: JSONObject,
    )

    companion object {
        private const val USER_AGENT = "YTDown/1.0.0 (allanosan@email.com)"
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val REQUEST_DELAY_MS = 1100L
        private const val MAX_TENTATIVAS = 3
        private const val BACKOFF_MS = 1500L

        private fun escapeLucenePhrase(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        /**
         * Monta a query Lucene de recording. Sem artista a cláusula é omitida —
         * buscar `artist:""` depende de o servidor tolerar frase vazia.
         */
        /**
         * Le a resposta de busca distinguindo estrangulamento de ausencia real.
         *
         * O MusicBrainz responde ao rate limit com `{"error": "...busy..."}` — e
         * nem sempre com status 5xx: a mesma query devolveu 503 com corpo valido
         * e 200 com corpo de erro. Colapsar os dois em "sem resultado" fazia a
         * banda parecer inexistente de forma intermitente.
         */
        internal fun parseSearchResponse(body: String?, httpCode: Int): SearchOutcome {
            val json = body?.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }.getOrNull()
            }
            if (json != null && json.has("error")) return SearchOutcome.RateLimited
            if (httpCode !in 200..299) return SearchOutcome.RateLimited
            val recordings = json?.optJSONArray("recordings") ?: return SearchOutcome.Empty
            if (recordings.length() == 0) return SearchOutcome.Empty
            return SearchOutcome.Ok(recordings)
        }

        /**
         * Escolhe de qual gravacao/lancamento a faixa saiu originalmente.
         *
         * A coletanea nem sempre esta dentro de um recording: para "Enough Is
         * Enough" do Whitecross cada recording traz um release so, e o primeiro
         * da lista e "The Very Best Of Whitecross". Pegar `recordings[0]`
         * gravava a coletanea como album. A selecao percorre todos os pares
         * (recording, release), descarta os tipos rejeitados e fica com a data
         * mais antiga — o lancamento original, nao a regravacao nem a coletanea.
         */
        internal fun pickOriginalRecording(
            recordings: JSONArray,
            artist: String,
        ): ReleaseChoice? {
            val pares = mutableListOf<ReleaseChoice>()
            for (i in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(i) ?: continue
                val releases = recording.optJSONArray("releases") ?: continue
                for (j in 0 until releases.length()) {
                    releases.optJSONObject(j)?.let { pares.add(ReleaseChoice(recording, it)) }
                }
            }
            if (pares.isEmpty()) return null
            val originais = pares.filter { par ->
                secondaryTypes(par.release).none { it in TIPOS_REJEITADOS }
            }
            // Sem nenhum original a coletanea ainda e melhor que album vazio.
            val candidatos = originais.ifEmpty { pares }
            return candidatos.minByOrNull { par ->
                // Data vazia vai para o fim: sem data nao da para provar que e a origem.
                par.release.optString("date").ifBlank { "9999" }
            }
        }

        /** Secondary-types que nunca sao o album de origem da faixa. */
        private val TIPOS_REJEITADOS = setOf("compilation")

        private fun secondaryTypes(release: JSONObject): List<String> {
            val tipos = release.optJSONObject("release-group")
                ?.optJSONArray("secondary-types")
                ?: return emptyList()
            return (0 until tipos.length()).map { tipos.optString(it).lowercase() }
        }

        internal fun buildRecordingQuery(title: String, artist: String): String {
            val recording = "recording:\"${escapeLucenePhrase(title.trim())}\""
            val cleanArtist = artist.trim()
            if (cleanArtist.isEmpty()) return recording
            return "$recording AND artist:\"${escapeLucenePhrase(cleanArtist)}\""
        }
    }

    suspend fun searchArtistId(name: String): String? = withContext(io) {
        try {
            delay(REQUEST_DELAY_MS)
            val query = java.net.URLEncoder.encode(name, "UTF-8")
            val url = "$BASE/artist/?query=artist:\"$query\"&fmt=json"
            val json = fetchJson(url) ?: return@withContext null
            val artists = json.optJSONArray("artists") ?: return@withContext null
            if (artists.length() == 0) return@withContext null
            
            // Retorna o MBID do primeiro resultado (melhor correspondência)
            artists.getJSONObject(0).optString("id")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchRecording(
        title: String,
        artist: String
    ): MusicBrainzRecording? = withContext(io) {
        return@withContext try {
            delay(1100L) // Rate limit
            val query = buildRecordingQuery(title, artist)
            val url = "https://musicbrainz.org/ws/2/recording/?query=${java.net.URLEncoder.encode(query, "UTF-8")}&fmt=json&inc=artists+releases+release-groups"

            // Sob rate limit a resposta e um corpo de erro, as vezes com HTTP 200.
            // Desistir na primeira fazia a banda parecer inexistente.
            var recordings: JSONArray? = null
            for (tentativa in 0 until MAX_TENTATIVAS) {
                if (tentativa > 0) delay(BACKOFF_MS * tentativa)
                val resposta = http.get(url)
                when (val resultado = parseSearchResponse(resposta.body, resposta.code)) {
                    is SearchOutcome.Ok -> {
                        recordings = resultado.recordings
                        break
                    }
                    SearchOutcome.Empty -> return@withContext null
                    SearchOutcome.RateLimited -> continue
                }
            }
            val encontrados = recordings ?: return@withContext null

            val escolha = pickOriginalRecording(encontrados, artist) ?: return@withContext null
            val item = escolha.recording
            val recordingMbid = item.optString("id")
            val release: JSONObject? = escolha.release
            val releaseMbid = release?.optString("id")
            val releaseGroupId = release?.optJSONObject("release-group")?.optString("id")
            
            val artistCredit = item.optJSONArray("artist-credit")
            val artistObject = artistCredit?.optJSONObject(0)
            val artistMetadata = artistObject?.optJSONObject("artist")
            val artistMbid = artistMetadata?.optString("id")

            // Extrair ano do release (first-release-date do release ou date)
            val releaseDate = release?.optString("date") ?: ""
            val year = releaseDate.takeIf { it.length >= 4 }?.substring(0, 4)

            // PASSO 2: Obter número da faixa via endpoint /release (conforme solicitado pelo usuário)
            var trackNumber: String? = null
            var discNumber: String? = null
            
            if (releaseMbid != null) {
                delay(REQUEST_DELAY_MS)
                val releaseUrl = "$BASE/release/$releaseMbid?inc=recordings+media&fmt=json"
                val releaseJson = fetchJson(releaseUrl)
                val media = releaseJson?.optJSONArray("media")
                if (media != null) {
                    outer@for (m in 0 until media.length()) {
                        val medium = media.getJSONObject(m)
                        val tracks = medium.optJSONArray("tracks") ?: continue
                        for (t in 0 until tracks.length()) {
                            val track = tracks.getJSONObject(t)
                            if (track.optJSONObject("recording")?.optString("id") == recordingMbid) {
                                trackNumber = track.optString("number")
                                discNumber = medium.optInt("position", 1).toString().takeIf { it != "1" }
                                break@outer
                            }
                        }
                    }
                }
            }

            MusicBrainzRecording(
                title = item.optString("title"),
                artist = artistMetadata?.optString("name") ?: artistObject?.optString("name") ?: artist,
                album = release?.optString("title") ?: "",
                releaseId = releaseMbid,
                releaseGroupId = releaseGroupId,
                artistId = artistMbid,
                year = year,
                trackNumber = trackNumber,
                discNumber = discNumber
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Busca todas as tags populares de metal
     */
    suspend fun getPopularMetalTags(): List<String> = withContext(io) {
        listOf(
            "black metal", "death metal", "power metal", "thrash metal",
            "heavy metal", "doom metal", "symphonic metal", "gothic metal",
            "progressive metal", "metalcore", "deathcore", "blackened death metal",
            "melodic death metal", "traditional metal", "speed metal", "industrial metal",
            "neoclassical metal", "christian metal", "white metal", "hair metal",
            "slam death metal", "technical death metal", "avant-garde metal"
        )
    }

    suspend fun getArtistDetails(mbid: String): MBBandDetails? = withContext(io) {
        val json = lookupArtist(mbid, inc = "tags,genres,aliases,url-rels") ?: return@withContext null
        parseArtistDetails(json)
    }

    suspend fun getArtistReleaseGroups(mbid: String): List<MBReleaseGroup> = withContext(io) {
        delay(REQUEST_DELAY_MS)
        val url = "$BASE/release-group/?artist=$mbid&fmt=json"
        val json = fetchJson(url) ?: return@withContext emptyList()
        val groups = json.optJSONArray("release-groups") ?: return@withContext emptyList()
        
        (0 until groups.length()).map { i ->
            val group = groups.getJSONObject(i)
            MBReleaseGroup(
                id = group.optString("id"),
                title = group.optString("title"),
                firstReleaseDate = group.optString("first-release-date", ""),
                primaryType = group.optString("primary-type", ""),
                secondaryTypes = group.optJSONArray("secondary-types")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                artistCredit = group.optString("artist-credit", "")
            )
        }
    }

    /**
     * Obtém as tags de um artista usando seu MBID.
     * @param mbid MusicBrainz ID do artista
     * @return Lista de tags do artista
     */
    suspend fun getArtistTags(mbid: String): List<String> = withContext(io) {
        val details = getArtistDetails(mbid)
        details?.tags ?: emptyList()
    }

    /**
     * Obtém as tags de um artista pelo nome.
     * Primeiro busca o MBID pelo nome, depois busca as tags.
     * @param artistName Nome do artista
     * @return Lista de tags do artista
     */
    suspend fun getArtistTagsByName(artistName: String): List<String> = withContext(io) {
        try {
            val mbid = searchArtistId(artistName) ?: return@withContext emptyList()
            getArtistTags(mbid)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca artistas por query string.
     * @param query Termo de busca
     * @param limit Número máximo de resultados (padrão: 25, máximo: 100)
     * @see <a href="https://musicbrainz.org/doc/MusicBrainz_API">MusicBrainz API</a>
     */
    suspend fun searchArtists(query: String, limit: Int = 25): List<MBArtist> = withContext(io) {
        delay(REQUEST_DELAY_MS)
        val effectiveLimit = limit.coerceIn(1, 100)
        val url = "$BASE/artist/?query=$query&fmt=json&limit=$effectiveLimit"
        val json = fetchJson(url) ?: return@withContext emptyList()
        val artists = json.optJSONArray("artists") ?: return@withContext emptyList()

        (0 until artists.length()).map { i ->
            parseArtist(artists.getJSONObject(i))
        }
    }

    /**
     * Busca artistas por tag do MusicBrainz.
     * @param tag Tag (gênero) para busca
     * @param limit Número máximo de resultados (padrão: 25, máximo: 100)
     * @see <a href="https://musicbrainz.org/doc/MusicBrainz_API/Search">MusicBrainz Search</a>
     */
    suspend fun searchArtistsByTag(tag: String, limit: Int = 25): List<MBArtist> = withContext(io) {
        delay(REQUEST_DELAY_MS)
        val effectiveLimit = limit.coerceIn(1, 100)
        // Busca usando a sintaxe de tag do MusicBrainz
        val url = "$BASE/artist/?query=tag:$tag&fmt=json&limit=$effectiveLimit"
        val json = fetchJson(url) ?: return@withContext emptyList()
        val artists = json.optJSONArray("artists") ?: return@withContext emptyList()

        (0 until artists.length()).map { i ->
            parseArtist(artists.getJSONObject(i))
        }
    }

    /**
     * Descobre artistas semelhantes usando relaciones do MusicBrainz
     * @param bandName Nome da banda
     * @return Lista de bandas similares
     */
    suspend fun discoverSimilarBands(bandName: String): MBDiscoveryResponse = withContext(io) {
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
    suspend fun getArea(areaId: String): MBArea? = withContext(io) {
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

    private suspend fun fetchJson(urlString: String): JSONObject? {
        val resposta = http.get(urlString)
        if (resposta.code != 200) return null
        val body = resposta.body ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
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