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

@Singleton
class MusicBrainzService @Inject constructor() {

    companion object {
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"
        private const val REQUEST_DELAY_MS = 1100L
    }

    suspend fun discoverSimilarBands(bandName: String): MBDiscoveryResponse =
        withContext(Dispatchers.IO) {
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

                val genre = artistDetails.optString("type", "Artist")

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
                                genre = genre,
                                country = target.optString("country").takeIf { it.isNotBlank() }
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

    suspend fun getBandDetails(bandName: String): MBDetailsResponse =
        withContext(Dispatchers.IO) {
            try {
                val artistId = searchArtistId(bandName) ?: return@withContext MBDetailsResponse(
                    success = false,
                    error = "Artista '$bandName' não encontrado."
                )

                val artist = lookupArtist(artistId, inc = "tags,url-rels")
                    ?: return@withContext MBDetailsResponse(
                        success = false,
                        error = "Não foi possível buscar detalhes."
                    )

                val genre = parseGenreFromArtist(artist)
                val imageUrl = parseImageUrl(artist, bandName)

                MBDetailsResponse(
                    success = true,
                    name = bandName,
                    genre = genre,
                    image_url = imageUrl
                )
            } catch (e: Exception) {
                MBDetailsResponse(success = false, error = "Erro: ${e.message}")
            }
        }

    suspend fun getBandAlbums(bandName: String): MBAlbumsResponse =
        withContext(Dispatchers.IO) {
            try {
                val artistId = searchArtistId(bandName) ?: return@withContext MBAlbumsResponse(
                    success = false,
                    error = "Artista '$bandName' não encontrado."
                )

                val releaseGroups = browseReleaseGroups(artistId)
                if (releaseGroups.isEmpty()) {
                    return@withContext MBAlbumsResponse(
                        success = false,
                        error = "Nenhum álbum encontrado."
                    )
                }

                val albums = releaseGroups.map { rg ->
                    MBAlbum(
                        id = rg.optString("id"),
                        name = rg.optString("name"),
                        year = rg.optString("first-release-date", "").take(4).ifBlank { "?" }
                    )
                }

                MBAlbumsResponse(success = true, albums = albums)
            } catch (e: Exception) {
                MBAlbumsResponse(success = false, error = "Erro: ${e.message}")
            }
        }

    suspend fun searchArtistId(name: String): String? {
        delay(REQUEST_DELAY_MS)
        val q = URLEncoder.encode(name, "UTF-8")
        val json = fetchJson("$BASE/artist?query=$q&limit=1&fmt=json") ?: return null
        val artists = json.optJSONArray("artists") ?: return null
        if (artists.length() == 0) return null
        return artists.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
    }

    private suspend fun lookupArtist(mbid: String, inc: String): JSONObject? {
        delay(REQUEST_DELAY_MS)
        return fetchJson("$BASE/artist/$mbid?inc=$inc&fmt=json")
    }

    private suspend fun browseReleaseGroups(artistMbid: String): List<JSONObject> {
        delay(REQUEST_DELAY_MS)
        val json = fetchJson(
            "$BASE/release-group?artist=$artistMbid&type=album|studio&limit=100&fmt=json"
        ) ?: return emptyList()

        val rgList = json.optJSONArray("release-groups") ?: return emptyList()
        return (0 until rgList.length()).mapNotNull { rgList.optJSONObject(it) }
    }

    private fun parseGenreFromArtist(artist: JSONObject): String? {
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

    private fun parseImageUrl(artist: JSONObject, bandName: String): String? {
        val urls = artist.optJSONArray("urls")
        if (urls != null) {
            for (i in 0 until urls.length()) {
                val url = urls.getJSONObject(i)
                val rel = url.optJSONObject("relation")
                val targetType = rel?.optString("target-type")
                if (targetType == "Url") {
                    val resource = rel.optString("resource", "")
                    if (resource.contains("coverartarchive.org") || resource.contains("wikipedia.org")) {
                        return resource
                    }
                }
            }
        }
        return "https://coverartarchive.org/artist/$bandName/front-250"
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 12000
                readTimeout = 12000
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
}

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