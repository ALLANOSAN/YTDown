package com.example.ytdown.core.artwork

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FanArtTvService @Inject constructor(private val client: OkHttpClient) {
    private val apiKey = "f3db96ca2d95eaeba2227bd3fb51e192"
    private val baseUrl = "https://webservice.fanart.tv/v3/music"

    suspend fun getArtistArtwork(artistMbid: String): String? {
        val url = "$baseUrl/$artistMbid?api_key=$apiKey"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                
                // Tentar pegar o 'artistthumb' ou 'musicbanner'
                val thumbs = json.optJSONArray("artistthumb")
                thumbs?.optJSONObject(0)?.optString("url")
            }
        } catch (e: Exception) {
            Log.e("FanArtTvService", e.stackTraceToString())
            null
        }
    }
}