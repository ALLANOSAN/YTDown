package com.example.ytdown.services

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsService @Inject constructor() {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://lrclib.net/api/get"

    suspend fun getLyrics(artist: String, title: String, album: String? = null, duration: Int? = null): LyricsResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl?artist=${java.net.URLEncoder.encode(artist, "UTF-8")}&track=${java.net.URLEncoder.encode(title, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body?.string(), LyricsResponse::class.java)
            }
        } catch (e: Exception) { null }
    }
}
data class LyricsResponse(val plainLyrics: String?, val syncedLyrics: String?)
