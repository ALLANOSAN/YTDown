package com.example.ytdown.core.artwork

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtArchiveService @Inject constructor() {
    private val client = OkHttpClient()

    suspend fun fetchAlbumCover(releaseMbid: String): String? {
        if (releaseMbid.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url("https://coverartarchive.org/release/$releaseMbid")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                val images = json.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    // Tenta encontrar a capa frontal (Front)
                    for (i in 0 until images.length()) {
                        val img = images.getJSONObject(i)
                        if (img.optBoolean("front")) {
                            return img.getJSONObject("thumbnails").optString("large")
                        }
                    }
                    images.getJSONObject(0).getJSONObject("thumbnails").optString("large")
                } else null
            }
        } catch (e: Exception) {
            Log.e("Artwork", "CoverArtArchive error: ${e.message}")
            null
        }
    }
}
