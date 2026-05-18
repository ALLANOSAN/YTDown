package com.example.ytdown.core.artwork

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover Art Archive Service - Integração com a API do Cover Art Archive
 *
 * Documentação oficial: https://musicbrainz.org/doc/Cover_Art_Archive/API
 *
 * Endpoints utilizados:
 * - /release/{mbid} - Lista todas as capas disponíveis
 * - /release/{mbid}/front - Retorna a capa frontal (redirect)
 * - /release-group/{mbid}/front - Retorna a capa frontal do grupo de lançamento
 */
@Singleton
class CoverArtArchiveService @Inject constructor() {
    private val client = OkHttpClient()

    /**
     * Busca a URL da capa de um release.
     * @param releaseMbid MBID do release
     * @return URL da capa ou null
     */
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

    /**
     * Baixa a capa do álbum em bytes.
     * @param releaseMbid MBID do release
     * @return ByteArray da imagem ou null
     * @see <a href="https://musicbrainz.org/doc/Cover_Art_Archive/API">Cover Art Archive API</a>
     */
    suspend fun downloadAlbumArt(releaseMbid: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Primeiro busca a URL da capa
            val coverUrl = fetchAlbumCover(releaseMbid) ?: return@withContext null

            // Agora faz o download da imagem
            val request = Request.Builder()
                .url(coverUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("Artwork", "CoverArtArchive download error: ${e.message}")
            null
        }
    }

    /**
     * Busca a melhor capa para um release group.
     * @param releaseGroupMbid MBID do release group
     * @return URL da capa ou null
     */
    suspend fun fetchReleaseGroupCover(releaseGroupMbid: String): String? {
        if (releaseGroupMbid.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url("https://coverartarchive.org/release-group/$releaseGroupMbid/front")
                .build()

            // O endpoint retorna um redirect para a imagem
            client.newCall(request).execute().use { response ->
                if (response.code == 307 || response.code == 302) {
                    response.header("Location")
                } else null
            }
        } catch (e: Exception) {
            Log.e("Artwork", "CoverArtArchive release-group error: ${e.message}")
            null
        }
    }
}
