package com.example.ytdown.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover Art Archive Service - Integração profissional com a API oficial
 * 
 * Documentação: https://musicbrainz.org/doc/Cover_Art_Archive/API
 * 
 * Endpoints suportados:
 * - /release-group/{mbid} - Busca capas de release groups
 * - /release/{mbid} - Busca capas de releases específicos
 * - /release-group/{mbid}/front -直接获取封面图片URL
 * 
 * Formatos suportados:
 * - JPEG (maior qualidade)
 * - PNG (quando disponível)
 * - Thumbnail (250px)
 * - Front (300px)
 * - Back (quando disponível)
 */
@Singleton
class CoverArtArchiveService @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://coverartarchive.org"
        private const val USER_AGENT = "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"
        
        // Delay obrigatório para respeitar rate limit do MusicBrainz
        // O Cover Art Archive compartilha o mesmo rate limit
        private const val REQUEST_DELAY_MS = 1100L
        
        // Timeout configs
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    /**
     * Busca a capa frontal de um release group (álbum)
     * @param releaseGroupMBID O MBID do release group (obtido do MusicBrainz)
     * @return URL da imagem ou null se não encontrada
     */
    suspend fun getFrontCover(releaseGroupMBID: String): String? = withContext(Dispatchers.IO) {
        try {
            // Tenta primeiro o endpoint específico de front
            delay(REQUEST_DELAY_MS)
            val url = URL("$BASE_URL/release-group/$releaseGroupMBID/front")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            
            // Se retornar redirect (307), tenta seguir
            if (connection.responseCode == 307 || connection.responseCode == 302) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                return@withContext newUrl
            }
            
            // Se não encontrou no release-group, tenta no release diretamente
            if (connection.responseCode == 404) {
                connection.disconnect()
                return@withContext null
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val json = JSONObject(response)
            val images = json.optJSONArray("images")
            
            var result: String? = null
            if (images != null) {
                for (i in 0 until images.length()) {
                    val image = images.getJSONObject(i)
                    if (image.optBoolean("front", false)) {
                        val thumbnails = image.optJSONObject("thumbnails")
                        result = thumbnails?.optString("250") 
                            ?: image.optString("image")
                        break
                    }
                }
            }
            
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Busca todas as capas disponíveis de um release group
     * @param releaseGroupMBID O MBID do release group
     * @return Lista de CoverArtInfo com URLs e metadados
     */
    suspend fun getAllCovers(releaseGroupMBID: String): List<CoverArtInfo> = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = URL("$BASE_URL/release-group/$releaseGroupMBID")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            
            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext emptyList()
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val json = JSONObject(response)
            val images = json.optJSONArray("images") ?: JSONArray()
            
            val covers = mutableListOf<CoverArtInfo>()
            for (i in 0 until images.length()) {
                val image = images.getJSONObject(i)
                val thumbnails = image.optJSONObject("thumbnails")
                
                covers.add(
                    CoverArtInfo(
                        url = image.optString("image"),
                        thumbnails = CoverArtThumbnails(
                            small = thumbnails?.optString("120"),
                            medium = thumbnails?.optString("250"),
                            large = thumbnails?.optString("500"),
                            original = thumbnails?.optString("1200")
                        ),
                        isFront = image.optBoolean("front", false),
                        isBack = image.optBoolean("back", false),
                        comment = image.optString("comment", "")
                    )
                )
            }
            
            covers
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca capa por release MBID (mais específico)
     * @param releaseMBID O MBID do release
     * @return URL da imagem ou null
     */
    suspend fun getCoverByRelease(releaseMBID: String): String? = withContext(Dispatchers.IO) {
        try {
            delay(REQUEST_DELAY_MS)
            val url = URL("$BASE_URL/release/$releaseMBID/front")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            
            if (connection.responseCode == 307 || connection.responseCode == 302) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                return@withContext newUrl
            }
            
            if (connection.responseCode == 200) {
                // Retorna diretamente a URL da imagem
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                return@withContext redirectUrl
            }
            
            connection.disconnect()
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Versão mais robusta que tenta múltiplas estratégias
     * @param releaseGroupMBID MBID do release group
     * @param releaseMBID MBID do release (opcional)
     * @return URL da melhor capa disponível
     */
    suspend fun getBestCover(releaseGroupMBID: String, releaseMBID: String? = null): String? = withContext(Dispatchers.IO) {
        // Estratégia 1: Tentar release group front
        getFrontCover(releaseGroupMBID)?.let { return@withContext it }
        
        // Estratégia 2: Tentar release específico
        releaseMBID?.let {
            getCoverByRelease(it)?.let { cover -> return@withContext cover }
        }
        
        // Estratégia 3: Buscar todas e pegar a frontal
        val allCovers = getAllCovers(releaseGroupMBID)
        allCovers.find { it.isFront }?.thumbnails?.large
            ?: allCovers.find { it.isFront }?.thumbnails?.medium
            ?: allCovers.firstOrNull()?.thumbnails?.large
            ?: allCovers.firstOrNull()?.thumbnails?.medium
            ?: allCovers.firstOrNull()?.url
    }

    /**
     * Baixa a capa do álbum em bytes.
     * @param releaseMBID MBID do release
     * @return ByteArray da imagem ou null
     * @see <a href="https://musicbrainz.org/doc/Cover_Art_Archive/API">Cover Art Archive API</a>
     */
    suspend fun downloadAlbumArt(releaseMBID: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Primeiro busca a URL da melhor capa
            val coverUrl = getBestCover(releaseMBID) ?: return@withContext null

            // Agora faz o download da imagem
            val url = URL(coverUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            if (connection.responseCode == 200) {
                val bytes = connection.inputStream.readBytes()
                connection.disconnect()
                return@withContext bytes
            }

            connection.disconnect()
            null
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Dados da capa do álbum
 */
data class CoverArtInfo(
    val url: String,
    val thumbnails: CoverArtThumbnails,
    val isFront: Boolean,
    val isBack: Boolean,
    val comment: String
)

/**
 * Thumbnails em diferentes tamanhos
 */
data class CoverArtThumbnails(
    val small: String?,     // 120px
    val medium: String?,    // 250px
    val large: String?,     // 500px
    val original: String?   // 1200px+
)