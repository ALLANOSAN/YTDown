package com.example.ytdown.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O MusicBrainz responde ao rate limit com um corpo de erro, as vezes com HTTP
 * 200. Sem retry a busca desistia na primeira resposta estrangulada e a banda
 * aparecia como inexistente — era isso que fazia o Whitecross sumir de forma
 * intermitente.
 *
 * A quebra que estes testes pegam: reduzir MAX_TENTATIVAS para 1, ou trocar o
 * `continue` do ramo RateLimited por um `return null`, faz a busca devolver
 * nulo mesmo com o servidor respondendo certo na tentativa seguinte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MusicBrainzRetryTest {

    /**
     * Cliente de mentira: devolve as respostas na ordem em que foram enfileiradas
     * para cada URL e registra o que foi pedido. Mora em test sources — a classe
     * de producao nao ganha nada so para o teste existir.
     */
    private class FakeHttpClient(
        private val respostasBusca: MutableList<HttpResponse>,
    ) : MusicBrainzHttpClient {
        val urlsPedidas = mutableListOf<String>()

        override suspend fun get(url: String): HttpResponse {
            urlsPedidas.add(url)
            // Lookup de faixa (/release/...) nao interessa aqui: 404 deixa
            // trackNumber nulo sem inventar dado que o teste nao verifica.
            if (!url.contains("/recording/")) return HttpResponse(404, null)
            return respostasBusca.removeFirstOrNull() ?: HttpResponse(404, null)
        }
    }

    private val corpoOcupado =
        """{"error": "The MusicBrainz web server is currently busy. Please try again later."}"""

    /** Resposta real da API para `recording:"Love on the Line" AND artist:"Whitecross"`. */
    private val corpoBusca = """
        {"count": 2, "recordings": [{
          "id": "31e6cbe0-d0aa-4505-ab7a-262b692acadc",
          "title": "Love on the Line",
          "artist-credit": [{"name": "Whitecross", "artist": {
            "id": "51f24fe4-6cb7-477a-a6ed-6be19abf99bb", "name": "Whitecross",
            "sort-name": "Whitecross"}}],
          "releases": [
            {"id": "d44c85bf-1ce7-41c4-9c70-ea817ccec6c6", "title": "Heavy Righteous Metal",
             "date": "1988", "release-group": {"id": "94d66b27-e8ee-34bc-a9be-c44dca26867b",
             "primary-type": "Album", "secondary-types": ["Compilation"]}},
            {"id": "1e35af1d-3a76-4d9f-ac08-51589031276f", "title": "Love on the Line",
             "date": "1988", "release-group": {"id": "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
             "primary-type": "Album"}},
            {"id": "b205355d-9dc2-4eeb-a937-f90fc71ed62e", "title": "Ready to Rock",
             "date": null, "release-group": {"id": "1a4f8c1a-8170-3751-9300-a0fe9cf8362e",
             "primary-type": "Album", "secondary-types": ["Compilation"]}}
          ]}]}
    """.trimIndent()

    @Test
    fun `insiste depois do rate limit e devolve o album em vez de nulo`() = runTest {
        val http = FakeHttpClient(
            mutableListOf(
                HttpResponse(200, corpoOcupado),
                HttpResponse(503, corpoOcupado),
                HttpResponse(200, corpoBusca),
            )
        )
        val service = MusicBrainzService(http, StandardTestDispatcher(testScheduler))

        val resultado = service.searchRecording("Love on the Line", "Whitecross")

        assertEquals("Love on the Line", resultado?.album)
        assertEquals(3, http.urlsPedidas.count { it.contains("/recording/") })
    }

    @Test
    fun `busca sem resultado nao gasta tentativa extra`() = runTest {
        val semResultado = """{"count":0,"offset":0,"recordings":[]}"""
        val http = FakeHttpClient(mutableListOf(HttpResponse(200, semResultado)))
        val service = MusicBrainzService(http, StandardTestDispatcher(testScheduler))

        val resultado = service.searchRecording("Faixa Que Nao Existe", "Whitecross")

        assertNull(resultado)
        assertEquals(1, http.urlsPedidas.count { it.contains("/recording/") })
    }

    @Test
    fun `desiste depois de esgotar as tentativas em vez de insistir para sempre`() = runTest {
        val http = FakeHttpClient(
            mutableListOf(
                HttpResponse(503, corpoOcupado),
                HttpResponse(503, corpoOcupado),
                HttpResponse(503, corpoOcupado),
            )
        )
        val service = MusicBrainzService(http, StandardTestDispatcher(testScheduler))

        val resultado = service.searchRecording("Love on the Line", "Whitecross")

        assertNull(resultado)
        assertEquals(3, http.urlsPedidas.count { it.contains("/recording/") })
    }
}
