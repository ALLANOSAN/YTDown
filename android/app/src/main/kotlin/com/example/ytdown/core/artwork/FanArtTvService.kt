package com.example.ytdown.core.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FanArtTvService @Inject constructor() {

    private val client = OkHttpClient()

    companion object {
        private const val API_KEY = "f3db96ca2d95eaeba2227bd3fb51e192" // Utilizando a chave de API funcional
        private const val BASE_URL = "https://webservice.fanart.tv/v3"
    }

    suspend fun fetchArtistImageUrl(
        musicBrainzId: String
    ): String? = withContext(Dispatchers.IO) {

        return@withContext try {

            val request = Request.Builder()
                .url(
                    "$BASE_URL/music/$musicBrainzId?api_key=$API_KEY"
                )
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext null
            }

            val body = response.body?.string()
                ?: return@withContext null

            val json = JSONObject(body)
            val artists = json.optJSONArray("artistthumb")

            if (artists != null && artists.length() > 0) {
                val list = mutableListOf<JSONObject>()
                for (i in 0 until artists.length()) {
                    list.add(artists.getJSONObject(i))
                }
                
                // Ordenar por likes decrescente e pegar o primeiro
                val best = list.sortedByDescending { 
                    val likesStr = it.optString("likes", "0")
                    likesStr.toIntOrNull() ?: 0
                }.firstOrNull()

                best?.optString("url")
            } else {
                null
            }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadArtistImage(
        artistId: String
    ): ByteArray? = withContext(Dispatchers.IO) {

        return@withContext try {

            val imageUrl =
                fetchArtistImageUrl(artistId)
                    ?: return@withContext null

            val request =
                Request.Builder()
                    .url(imageUrl)
                    .build()

            val response =
                client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext null
            }

            response.body?.bytes()

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}