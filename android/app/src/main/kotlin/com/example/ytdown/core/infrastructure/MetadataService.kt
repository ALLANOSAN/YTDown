package com.example.ytdown.core.infrastructure

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MetadataService @Inject constructor() {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val lastFmKey = "c0bc9642cd67227a10ce0a129981513b"

    suspend fun getArtwork(artist: String, album: String?, title: String): Map<String, String?> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String?>()
        
        // 1. Tentar iTunes (Geralmente a melhor qualidade)
        val itunesArt = fetchItunesArtwork(artist, album ?: title)
        result["albumArt"] = itunesArt
        
        // 2. Tentar LastFM se iTunes falhar ou para imagem do artista
        val lastFmInfo = fetchLastFmInfo(artist, album)
        result["artistArt"] = lastFmInfo["artistArt"]
        if (result["albumArt"] == null) {
            result["albumArt"] = lastFmInfo["albumArt"]
        }
        
        // 3. Tentar Deezer como fallback final
        if (result["albumArt"] == null) {
            result["albumArt"] = fetchDeezerArtwork(artist, title)
        }
        
        result
    }

    private fun fetchItunesArtwork(artist: String, term: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $term", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&media=music&limit=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results")
            if (results.size() > 0) {
                val artUrl = results[0].asJsonObject.get("artworkUrl100").asString
                // Upgrade para 1000x1000
                artUrl.replace("100x100bb", "1000x1000bb")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchLastFmInfo(artist: String, album: String?): Map<String, String?> {
        val info = mutableMapOf<String, String?>()
        try {
            // Artista
            val artistUrl = "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=${URLEncoder.encode(artist, "UTF-8")}&api_key=$lastFmKey&format=json"
            val artistResponse = client.newCall(Request.Builder().url(artistUrl).build()).execute().body?.string()
            val artistJson = gson.fromJson(artistResponse, JsonObject::class.java)
            info["artistArt"] = extractLastFmImage(artistJson.getAsJsonObject("artist"))

            // Álbum
            if (album != null) {
                val albumUrl = "https://ws.audioscrobbler.com/2.0/?method=album.getinfo&artist=${URLEncoder.encode(artist, "UTF-8")}&album=${URLEncoder.encode(album, "UTF-8")}&api_key=$lastFmKey&format=json"
                val albumResponse = client.newCall(Request.Builder().url(albumUrl).build()).execute().body?.string()
                val albumJson = gson.fromJson(albumResponse, JsonObject::class.java)
                info["albumArt"] = extractLastFmImage(albumJson.getAsJsonObject("album"))
            }
        } catch (e: Exception) { }
        return info
    }

    private fun extractLastFmImage(obj: JsonObject?): String? {
        return try {
            val images = obj?.getAsJsonArray("image") ?: return null
            // Pegar 'extralarge' ou a maior disponível
            images.last().asJsonObject.get("#text").asString.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDeezerArtwork(artist: String, title: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://api.deezer.com/search?q=$query&limit=1"
            val response = client.newCall(Request.Builder().url(url).build()).execute().body?.string()
            val json = gson.fromJson(response, JsonObject::class.java)
            val data = json.getAsJsonArray("data")
            if (data.size() > 0) {
                val album = data[0].asJsonObject.getAsJsonObject("album")
                album.get("cover_xl")?.asString ?: album.get("cover_big")?.asString
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
