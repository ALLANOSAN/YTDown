package com.example.ytdown.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resposta crua de uma chamada ao MusicBrainz. */
data class HttpResponse(val code: Int, val body: String?)

/**
 * Fronteira HTTP do MusicBrainz.
 *
 * Existe para que o retry sob rate limit possa ser testado sem rede: o servidor
 * so devolve `{"error": "...busy..."}` quando esta estrangulado, e isso nao da
 * para reproduzir de forma confiavel contra a API real.
 */
interface MusicBrainzHttpClient {
    suspend fun get(url: String): HttpResponse
}

/** Implementacao de producao sobre OkHttp. */
class OkHttpMusicBrainzClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val userAgent: String,
) : MusicBrainzHttpClient {

    override suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                HttpResponse(response.code, response.body?.string())
            }
        } catch (_: IOException) {
            // Queda de rede nao e ausencia de resultado. O codigo 0 nao e 2xx,
            // entao a leitura classifica como estrangulamento e a busca insiste
            // em vez de reportar "banda nao encontrada".
            HttpResponse(0, null)
        }
    }
}
