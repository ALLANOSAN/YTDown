package com.example.ytdown.core.infrastructure

import com.example.ytdown.utils.MemoryLruCache
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Singleton
class MetadataService @Inject constructor() {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val lastFmKey = "c0bc9642cd67227a10ce0a129981513b"
    
    private val artworkCache = MemoryLruCache<String, Map<String, String?>>(100)
    
    // ⚡ In-Flight Protection: Impede requisições duplicadas.
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Map<String, String?>>>()

    // 🧪 Test Overrides: Permite injetar fakes durante desenvolvimento.
    private var artistImageOverride: (suspend (String) -> String?)? = null

    fun setArtistImageOverride(override: (suspend (String) -> String?)?) {
        artistImageOverride = override
    }

    suspend fun getArtwork(artist: String, album: String?, title: String): Map<String, String?> = coroutineScope {
        val cacheKey = "art_${artist.trim()}_${album?.trim() ?: title.trim()}".lowercase()
        
        artworkCache.get(cacheKey)?.let { return@coroutineScope it }

        val request = inFlightRequests.getOrPut(cacheKey) {
            async(Dispatchers.IO) {
                try {
                    performArtworkLookup(artist, album, title).also {
                        artworkCache.put(cacheKey, it)
                    }
                } finally {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }
        
        request.await()
    }

    private suspend fun performArtworkLookup(artist: String, album: String?, title: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        
        // Aplica override se existir
        artistImageOverride?.let { 
            result["artistArt"] = it(artist)
            return result 
        }

        fetchItunesArtwork(artist, album ?: title)?.let { result["albumArt"] = it }
        
        val lastFm = fetchLastFmInfo(artist, album)
        if (result["albumArt"] == null) result["albumArt"] = lastFm["albumArt"]
        result["artistArt"] = lastFm["artistArt"]
        
        if (result["albumArt"] == null) {
            result["albumArt"] = fetchDeezerArtwork(artist, title)
        }
        
        return result
    }

    /**
     * 🛡️ HD Priority: Busca a maior imagem disponível (extralarge > large > medium).
     * Migrado do Flutter (LastFmService._getBestImage).
     */
    private fun extractLastFmImage(obj: JsonObject?): String? {
        return try {
            val images = obj?.getAsJsonArray("image") ?: return null
            val priority = listOf("extralarge", "large", "medium")
            
            for (sizeName in priority) {
                for (i in 0 until images.size()) {
                    val img = images[i].asJsonObject
                    if (img.get("size").asString == sizeName) {
                        val url = img.get("#text").asString
                        if (url.isNotEmpty() && !url.contains("2a96cbd8b46e442fc41c2b86b821562f")) {
                            return url
                        }
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    /**
     * 🧬 Loosely Matching: Regex agressivo para comparação de nomes.
     */
    private fun looselyMatches(source: String?, target: String): Boolean {
        if (source == null) return false
        val normalize = { s: String -> 
            s.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim() 
        }
        val s = normalize(source)
        val t = normalize(target)
        return s == t || s.contains(t) || t.contains(s)
    }

    private fun fetchItunesArtwork(artist: String, term: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $term", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&media=music&limit=1"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results")
            if (results.size() > 0) {
                return results[0].asJsonObject.get("artworkUrl100").asString.replace(Regex("\\d+x\\d+bb"), "1000x1000bb")
            }
            null
        } catch (e: Exception) { null }
    }

    private fun fetchLastFmInfo(artist: String, album: String?): Map<String, String?> {
        val info = mutableMapOf<String, String?>()
        try {
            val aUrl = "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=${URLEncoder.encode(artist, "UTF-8")}&api_key=$lastFmKey&format=json"
            val aResp = client.newCall(Request.Builder().url(aUrl).build()).execute().body?.string()
            val aJson = gson.fromJson(aResp, JsonObject::class.java)
            info["artistArt"] = extractLastFmImage(aJson.getAsJsonObject("artist"))

            if (album != null && album != "YTDown") {
                val alUrl = "https://ws.audioscrobbler.com/2.0/?method=album.getinfo&artist=${URLEncoder.encode(artist, "UTF-8")}&album=${URLEncoder.encode(album, "UTF-8")}&api_key=$lastFmKey&format=json"
                val alResp = client.newCall(Request.Builder().url(alUrl).build()).execute().body?.string()
                val alJson = gson.fromJson(alResp, JsonObject::class.java)
                info["albumArt"] = extractLastFmImage(alJson.getAsJsonObject("album"))
            }
        } catch (e: Exception) {}
        return info
    }

    private fun fetchDeezerArtwork(artist: String, title: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = "https://api.deezer.com/search?q=$query&limit=1"
            val resp = client.newCall(Request.Builder().url(url).build()).execute().body?.string() ?: return null
            val json = gson.fromJson(resp, JsonObject::class.java)
            val data = json.getAsJsonArray("data")
            if (data.size() > 0) {
                val alb = data[0].asJsonObject.getAsJsonObject("album")
                return alb.get("cover_xl")?.asString ?: alb.get("cover_big")?.asString
            }
            null
        } catch (e: Exception) { null }
    }
}
