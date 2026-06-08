package com.example.ytdown.services

import com.example.ytdown.BuildConfig
import com.example.ytdown.services.ObservabilityService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastfmService @Inject constructor(
    private val observabilityService: ObservabilityService
) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val cache = mutableMapOf<String, String?>()
    private val apiKey = BuildConfig.LASTFM_API_KEY

    init {
        android.util.Log.d("LastfmService", "API key loaded: ${!apiKey.isBlank()}")
    }

    suspend fun clearCache() {
        cache.clear()
    }

    suspend fun getArtistImage(artist: String): String? {
        if (artist.isBlank() || apiKey.isBlank()) return null
        return resolveCache("artist:$artist") {
            fetchLastFmImage("artist.getinfo", mapOf("artist" to artist))
                ?: fetchItunesArtwork(artist, entity = "musicArtist")
                ?: fetchDeezerArtistImage(artist)
        }
    }

    suspend fun getAlbumCover(artist: String, album: String): String? {
        if (artist.isBlank() || album.isBlank() || apiKey.isBlank()) {
            android.util.Log.w("LastfmService", "getAlbumCover skipped: artist=${!artist.isBlank()} album=${!album.isBlank()} apiKey=${!apiKey.isBlank()}")
            return null
        }
        android.util.Log.d("LastfmService", "getAlbumCover called: artist=\"$artist\" album=\"$album\"")
        return resolveCache("album:$artist:$album") {
            fetchLastFmImage("album.getinfo", mapOf("artist" to artist, "album" to album))
                ?: fetchItunesArtwork("$artist $album", entity = "album")
                ?: fetchDeezerAlbumCover(artist, album)
        }
    }

    suspend fun getTrackCover(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank() || apiKey.isBlank()) return null
        return resolveCache("track:$artist:$title") {
            fetchLastFmImage("track.getInfo", mapOf("artist" to artist, "track" to title))
                ?: fetchItunesArtwork("$artist $title", entity = "musicTrack")
                ?: fetchDeezerTrackCover(artist, title)
        }
    }

    private suspend fun resolveCache(key: String, loader: suspend () -> String?): String? {
        if (cache.containsKey(key)) return cache[key]
        val value = loader()
        cache[key] = value
        return value
    }

    private suspend fun fetchLastFmImage(method: String, params: Map<String, String>): String? {
        val url = buildUrl(method, params)
        val json = fetchJson(url) ?: return null
        return extractBestImage(json)
    }

    private fun buildUrl(method: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) -> "${k}=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        return "https://ws.audioscrobbler.com/2.0/?method=$method&api_key=$apiKey&format=json&autocorrect=1&$query"
    }

    private fun extractBestImage(json: JsonObject?): String? {
        if (json == null) return null

        // Last.fm retorna imagens como array: [{"#text": "url", "size": "small"}, ..., {"#text": "url", "size": "mega"}]
        // ✅ FIX: o código anterior fazia deepSearch("image") e checava isJsonPrimitive,
        // mas o resultado é um JsonArray — nunca era primitivo → sempre retornava null.
        val imageArrays = json.deepSearchArrays("image")
        val sizePreference = listOf("mega", "extralarge", "large", "medium", "small", "")

        for (imageArray in imageArrays) {
            val bySize = mutableMapOf<String, String>()
            imageArray.forEach { element ->
                if (element.isJsonObject) {
                    val text = element.asJsonObject["#text"]?.asString
                    val size = element.asJsonObject["size"]?.asString ?: ""
                    if (!text.isNullOrBlank()) bySize[size] = text
                }
            }
            for (size in sizePreference) {
                val url = bySize[size]
                if (!url.isNullOrBlank()) return url
            }
        }
        return null
    }

    private fun JsonObject.deepSearchArrays(key: String): List<com.google.gson.JsonArray> {
        val results = mutableListOf<com.google.gson.JsonArray>()
        for ((name, value) in entrySet()) {
            if (name == key && value.isJsonArray) results.add(value.asJsonArray)
            when {
                value.isJsonObject -> results += value.asJsonObject.deepSearchArrays(key)
                value.isJsonArray -> value.asJsonArray.forEach { el ->
                    if (el.isJsonObject) results += el.asJsonObject.deepSearchArrays(key)
                }
            }
        }
        return results
    }

    private suspend fun fetchJson(url: String): JsonObject? {
        android.util.Log.d("LastfmService", "fetchJson: $url")
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        android.util.Log.w("LastfmService", "fetchJson failed: HTTP ${it.code} for $url")
                        return@withContext null
                    }
                    val body = it.body?.string() ?: return@withContext null
                    android.util.Log.d("LastfmService", "fetchJson success: ${body.take(200)}...")
                    JsonParser.parseString(body).asJsonObject
                }
            } catch (ex: Exception) {
                android.util.Log.e("LastfmService", "fetchJson exception: ${ex.message}")
                observabilityService.trackError("LastfmService", "lastfm_fetch_failure: ${ex.message}")
                null
            }
        }
    }

    private suspend fun fetchItunesArtwork(term: String, entity: String): String? {
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$query&entity=$entity&limit=8"
        val json = fetchJson(url) ?: return null
        val results = json.getAsJsonArray("results") ?: return null
        for (element in results) {
            val obj = element.asJsonObject
            val artworkUrl = obj["artworkUrl100"]?.asString
            if (!artworkUrl.isNullOrBlank()) {
                // Upscale de 100x100 para 300x300 (qualidade melhor pro display)
                return artworkUrl.replace("100x100bb", "300x300bb")
            }
        }
        return null
    }

    private suspend fun fetchDeezerArtistImage(artist: String): String? {
        val query = java.net.URLEncoder.encode(artist, "UTF-8")
        val url = "https://api.deezer.com/search/artist?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("picture_big")?.asString
    }

    private suspend fun fetchDeezerAlbumCover(artist: String, album: String): String? {
        val query = java.net.URLEncoder.encode("$artist $album", "UTF-8")
        val url = "https://api.deezer.com/search/album?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("cover_big")?.asString
    }

    private suspend fun fetchDeezerTrackCover(artist: String, title: String): String? {
        val query = java.net.URLEncoder.encode("$artist $title", "UTF-8")
        val url = "https://api.deezer.com/search/track?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("album")?.asJsonObject?.get("cover_big")?.asString
    }
}
