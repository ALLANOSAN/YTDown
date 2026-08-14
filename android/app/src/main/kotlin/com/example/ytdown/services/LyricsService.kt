package com.example.ytdown.services

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import com.example.ytdown.utils.LocalLogger

@Singleton
class LyricsService @Inject constructor() {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://lrclib.net/api/get"

    suspend fun getLyrics(artist: String, title: String, album: String? = null, duration: Int? = null): LyricsResponse? = withContext(Dispatchers.IO) {
        try {
            val artistEncoded = java.net.URLEncoder.encode(artist, "UTF-8")
            val trackEncoded = java.net.URLEncoder.encode(title, "UTF-8")
            
            var url = "$baseUrl?artist=$artistEncoded&track=$trackEncoded"
            if (!album.isNullOrBlank()) {
                url += "&album_name=${java.net.URLEncoder.encode(album, "UTF-8")}"
            }
            if (duration != null && duration > 0) {
                url += "&duration=$duration"
            }
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YTDown (https://github.com/ALLANOSAN/APPDOWNLOADYOUTUBE)")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, LyricsResponse::class.java)
            }
        } catch (e: Exception) {
            LocalLogger.error("Erro ao buscar letras: ${e.message}", tag = "LyricsService")
            null
        }
    }
}

data class LyricsResponse(
    val id: Int,
    val name: String?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Int?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
