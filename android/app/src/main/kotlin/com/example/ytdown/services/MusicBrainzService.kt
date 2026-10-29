package com.example.ytdown.services

import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import com.example.ytdown.utils.MetadataUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicBrainz Service — origem das tags da biblioteca.
 *
 * Documentação oficial: https://musicbrainz.org/doc/MusicBrainz_API
 *
 * Rate Limit: 1 request por segundo (OBRIGATÓRIO)
 * User-Agent: Obrigatório para identificação
 *
 * Endpoints utilizados:
 * - /ws/2/recording - Identifica a faixa e o lançamento de origem
 * - /ws/2/artist - Resolve o mbid do artista pelo nome
 * - /ws/2/artist/{mbid} - Tags e gêneros do artista (MetadataFixWorker)
 *
 * A busca por área, release-group e tag saiu junto com a aba de descoberta:
 * eram usadas só por ela, e endpoint sem chamador vira código que ninguém
 * exercita mas todo mundo precisa manter.
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
         *
         * Titulo generico casa por aproximacao com outra musica ("Baby Back Now"
         * numa busca por "Now!" dos Scorpions, de 1965): sem conferir o titulo
         * do recording, a mais antiga vencia. So valem recordings com o titulo
         * buscado; sem nenhum, vale tudo (melhor que album vazio).
         */
        internal fun pickOriginalRecording(
            recordings: JSONArray,
            artist: String,
            title: String,
        ): ReleaseChoice? {
            val pares = mutableListOf<ReleaseChoice>()
            for (i in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(i) ?: continue
                if (!recording.optString("title").trim().equals(title.trim(), ignoreCase = true)) continue
                val releases = recording.optJSONArray("releases") ?: continue
                for (j in 0 until releases.length()) {
                    releases.optJSONObject(j)?.let { pares.add(ReleaseChoice(recording, it)) }
                }
            }
            // Nenhum titulo exato (ex.: sufixo "(stereo)"): aceita o titulo
            // buscado seguido de sufixo de versao. "Vale tudo" devolvia outra
            // musica da mesma banda e o arquivo ganhava o titulo dela.
            if (pares.isEmpty()) {
                for (i in 0 until recordings.length()) {
                    val recording = recordings.optJSONObject(i) ?: continue
                    if (!tituloComSufixoDeVersao(recording.optString("title"), title)) continue
                    val releases = recording.optJSONArray("releases") ?: continue
                    for (j in 0 until releases.length()) {
                        releases.optJSONObject(j)?.let { pares.add(ReleaseChoice(recording, it)) }
                    }
                }
            }
            if (pares.isEmpty()) return null
            // `artist:"Queen"` e frase Lucene: casa "Queen Latifah" e "The Queen
            // Tribute Band". Sem conferir o credito, a gravacao mais antiga de
            // OUTRO artista vencia e sobrescrevia titulo e artista do arquivo.
            // Nenhum do artista buscado: nao ha match. Quem chama mantem o que
            // ja sabia (ou tenta o fallback so por titulo, que confere o artista).
            val doArtista = pares.filter { artistaBate(it.recording, artist) }
            if (doArtista.isEmpty()) return null
            val originais = doArtista.filter { par ->
                secondaryTypes(par.release).none { it in TIPOS_REJEITADOS } &&
                    !deVariosArtistas(par.release)
            }
            // Single/broadcast do mesmo ano e mais antigo que a reemissao de
            // estudio — e o original de 1982 esta sem data. Sem preferencia por
            // album, o single virava a tag ("No One Like You / Toccami Toccami"
            // em vez de "Blackout"). Album de estudio vence; sem nenhum, vale
            // qualquer original; sem original, a coletanea ainda e melhor que
            // album vazio.
            val albuns = originais.filter { primaryType(it.release) == "album" }
            val candidatos = albuns.ifEmpty { originais }.ifEmpty { doArtista }
            return candidatos.minByOrNull { par ->
                // Data vazia vai para o fim: sem data nao da para provar que e a origem.
                par.release.optString("date").ifBlank { "9999" }
            }
        }

        /** "Love of My Life (stereo)" / "[Remastered]" / " - Live" ainda sao "Love of My Life". */
        private fun tituloComSufixoDeVersao(encontrado: String, buscado: String): Boolean {
            val e = MetadataUtils.normalizeForMatch(encontrado.trim())
            val b = MetadataUtils.normalizeForMatch(buscado.trim())
            if (b.isEmpty() || !e.startsWith(b)) return false
            val resto = e.substring(b.length).trimStart()
            return resto.isEmpty() || resto.first() in "([-"
        }

        /**
         * O artista buscado e um dos creditados no recording? Sem artista
         * buscado (fallback so por titulo) qualquer um vale — quem chama
         * confere depois. "The " inicial e ignorado: o YouTube e o MusicBrainz
         * divergem em "The Scorpions" / "Scorpions".
         */
        private fun artistaBate(recording: JSONObject, artist: String): Boolean {
            if (artist.isBlank()) return true
            val creditos = recording.optJSONArray("artist-credit") ?: return true
            val buscado = chaveDeArtista(artist)
            val nomes = (0 until creditos.length()).mapNotNull { i ->
                creditos.optJSONObject(i)?.let { c ->
                    c.optJSONObject("artist")?.optString("name")?.ifBlank { null }
                        ?: c.optString("name")
                }
            }
            return nomes.any { chaveDeArtista(it) == buscado }
        }

        private fun chaveDeArtista(nome: String): String =
            MetadataUtils.normalizeForMatch(nome.trim()).removePrefix("the ").trim()

        /** Secondary-types que nunca sao o album de origem da faixa. */
        private val TIPOS_REJEITADOS = setOf("compilation", "live")

        /** MBID fixo do artista especial "Various Artists" no MusicBrainz. */
        private const val VARIOUS_ARTISTS_MBID = "89ad4ac3-39f7-470e-963a-56509c546377"

        /**
         * Coletanea de varios artistas nem sempre tem secondary-type
         * "compilation": "96% Puddel Rock" so se denuncia pelo credito do
         * lancamento, e vencia "Crazy World" em "Wind of Change".
         */
        private fun deVariosArtistas(release: JSONObject): Boolean {
            val creditos = release.optJSONArray("artist-credit") ?: return false
            return (0 until creditos.length()).any { i ->
                val artista = creditos.optJSONObject(i)?.optJSONObject("artist")
                artista?.optString("id") == VARIOUS_ARTISTS_MBID ||
                    artista?.optString("name").equals("Various Artists", ignoreCase = true)
            }
        }

        private fun secondaryTypes(release: JSONObject): List<String> {
            val tipos = release.optJSONObject("release-group")
                ?.optJSONArray("secondary-types")
                ?: return emptyList()
            return (0 until tipos.length()).map { tipos.optString(it).lowercase() }
        }

        private fun primaryType(release: JSONObject): String =
            release.optJSONObject("release-group")
                ?.optString("primary-type")
                ?.lowercase().orEmpty()

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
            // limit=100: o padrao (25) devolveu 25 de 141 gravacoes de "Wind of
            // Change" e nenhuma trazia o Crazy World — a coletanea vencia.
            val url = "https://musicbrainz.org/ws/2/recording/?query=${java.net.URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=100&inc=artists+releases+release-groups"

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

            val escolha = pickOriginalRecording(encontrados, artist, title) ?: return@withContext null
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

    suspend fun getArtistDetails(mbid: String): MBBandDetails? = withContext(io) {
        val json = lookupArtist(mbid, inc = "tags,genres,aliases,url-rels") ?: return@withContext null
        parseArtistDetails(json)
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

    // =====================================================
    // AREAS - Países e regiões
    // =====================================================

// =====================================================
    // PRIVATE HELPERS - Métodos auxiliares
    // =====================================================
    
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

}

// =====================================================
// DATA MODELS - Modelos de dados
// =====================================================

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

// Modelos existentes mantidos para compatibilidade
