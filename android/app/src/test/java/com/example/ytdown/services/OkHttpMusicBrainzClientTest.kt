package com.example.ytdown.services

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Fronteira HTTP real: os testes montam um OkHttpClient com interceptor, entao
 * a chamada percorre a maquina do OkHttp de verdade (Call, Request, Response)
 * sem abrir socket. Nada de mock do cliente — o que se verifica e o contrato
 * que esta classe emite e traduz.
 */
class OkHttpMusicBrainzClientTest {

    private fun clienteQue(intercepta: (okhttp3.Interceptor.Chain) -> Response) =
        OkHttpClient.Builder().addInterceptor { chain -> intercepta(chain) }.build()

    private fun resposta(chain: okhttp3.Interceptor.Chain, code: Int, body: String?) =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("teste")
            .body((body ?: "").toResponseBody(null))
            .build()

    /**
     * A quebra que este teste pega: deixar a IOException escapar faz o
     * enriquecimento inteiro estourar em queda de rede momentanea, em vez de
     * virar mais uma tentativa.
     */
    @Test
    fun `queda de rede vira resposta sem codigo em vez de estourar`() = runTest {
        val client = OkHttpMusicBrainzClient(
            client = clienteQue { throw IOException("rede caiu") },
            userAgent = "YTDown/teste",
        )

        val resposta = client.get("https://musicbrainz.org/ws/2/recording/?query=x")

        assertEquals(0, resposta.code)
        assertNull(resposta.body)
    }

    /**
     * A quebra que este teste pega: soltar o User-Agent. O MusicBrainz exige
     * identificacao e bloqueia cliente anonimo — a busca inteira passaria a
     * falhar em producao sem nenhum sintoma local.
     */
    @Test
    fun `envia o User-Agent que identifica o app`() = runTest {
        var enviado: String? = null
        val client = OkHttpMusicBrainzClient(
            client = clienteQue { chain ->
                enviado = chain.request().header("User-Agent")
                resposta(chain, 200, "{}")
            },
            userAgent = "YTDown/1.0.0 (contato)",
        )

        client.get("https://musicbrainz.org/ws/2/recording/?query=x")

        assertEquals("YTDown/1.0.0 (contato)", enviado)
    }

    /**
     * A quebra que este teste pega: perder o codigo ou o corpo na traducao. A
     * leitura da resposta depende dos dois para separar estrangulamento de
     * ausencia real — sem eles todo 503 viraria "sem resultado".
     */
    @Test
    fun `repassa codigo e corpo da resposta sem alterar`() = runTest {
        val corpo = """{"error": "The MusicBrainz web server is currently busy."}"""
        val client = OkHttpMusicBrainzClient(
            client = clienteQue { chain -> resposta(chain, 503, corpo) },
            userAgent = "YTDown/teste",
        )

        val resposta = client.get("https://musicbrainz.org/ws/2/recording/?query=x")

        assertEquals(503, resposta.code)
        assertEquals(corpo, resposta.body)
    }
}
