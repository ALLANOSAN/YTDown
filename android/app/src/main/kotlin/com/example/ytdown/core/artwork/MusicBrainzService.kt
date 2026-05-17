package com.example.ytdown.core.artwork

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class MusicBrainzResult(
    val artistId: String?,
    val releaseId: String?
)

@Singleton
class MusicBrainzService @Inject constructor(
    private val client: OkHttpClient,
    private val pythonBridge: PythonMetadataBridge
) {
    private val userAgent = "YTDown/1.0 ( contact@example.com )"

    suspend fun fetchRecordingMetadata(artistName: String?, titleName: String, filename: String?): MusicBrainzResult? {
        return try {
            var searchArtist = artistName
            var searchTitle = titleName

            if (searchArtist.isNullOrBlank() || searchArtist == "Unknown") {
                val extracted = pythonBridge.extractMetadataFromFilename(filename ?: titleName)
                searchArtist = extracted["artist"] ?: "Unknown"
                searchTitle = extracted["title"] ?: (filename ?: titleName)
            }

            val query = "recording:\"$searchTitle\" AND artist:\"$searchArtist\""
            val url = "https://musicbrainz.org/ws/2/recording/?query=${java.net.URLEncoder.encode(query, "UTF-8")}&fmt=json"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                val recordings = json.optJSONArray("recordings")
                if (recordings != null && recordings.length() > 0) {
                    val first = recordings.getJSONObject(0)
                    val mbid = first.optString("id")
                    val releaseList = first.optJSONArray("releases")
                    val releaseMbid = releaseList?.optJSONObject(0)?.optString("id")
                    
                    MusicBrainzResult(mbid, releaseMbid)
                } else null
            }
        } catch (e: Exception) {
            Log.e("Artwork", "MusicBrainz error: ${e.message}")
            null
        }
    }
}
