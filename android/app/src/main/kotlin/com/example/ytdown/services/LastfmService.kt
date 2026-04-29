package com.example.ytdown.services

import com.example.ytdown.BuildConfig
import com.example.ytdown.services.ObservabilityService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
        if (artist.isBlank() || album.isBlank() || apiKey.isBlank()) return null
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
        val images = json.deepSearch("image")
        return images.firstOrNull { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }?.asString
    }

    private fun JsonObject.deepSearch(key: String): List<JsonElement> {
        val results = mutableListOf<JsonElement>()
        for ((name, value) in entrySet()) {
            if (name == key) {
                results.add(value)
            }
            when {
                value.isJsonObject -> results += value.asJsonObject.deepSearch(key)
                value.isJsonArray -> value.asJsonArray.forEach { element ->
                    if (element.isJsonObject) results += element.asJsonObject.deepSearch(key)
                }
            }
        }
        return results
    }

    private fun fetchJson(url: String): JsonObject? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JsonParser.parseString(body).asJsonObject
            }
        } catch (ex: Exception) {
            observabilityService.trackError("LastfmService", "lastfm_fetch_failure: ${ex.message}")
            null
        }
    }

    private fun fetchItunesArtwork(term: String, entity: String): String? {
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$query&entity=$entity&limit=8"
        val json = fetchJson(url) ?: return null
        val results = json.getAsJsonArray("results") ?: return null
        for (element in results) {
            val obj = element.asJsonObject
            val artworkUrl = obj["artworkUrl100"]?.asString
            if (!artworkUrl.isNullOrBlank()) return artworkUrl
        }
        return null
    }

    private fun fetchDeezerArtistImage(artist: String): String? {
        val query = java.net.URLEncoder.encode(artist, "UTF-8")
        val url = "https://api.deezer.com/search/artist?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("picture_big")?.asString
    }

    private fun fetchDeezerAlbumCover(artist: String, album: String): String? {
        val query = java.net.URLEncoder.encode("$artist $album", "UTF-8")
        val url = "https://api.deezer.com/search/album?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("cover_big")?.asString
    }

    private fun fetchDeezerTrackCover(artist: String, title: String): String? {
        val query = java.net.URLEncoder.encode("$artist $title", "UTF-8")
        val url = "https://api.deezer.com/search/track?q=$query&limit=5"
        val json = fetchJson(url) ?: return null
        val data = json.getAsJsonArray("data") ?: return null
        return data.firstOrNull()?.asJsonObject?.get("album")?.asJsonObject?.get("cover_big")?.asString
    }
}
