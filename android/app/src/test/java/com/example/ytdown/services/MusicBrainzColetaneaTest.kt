package com.example.ytdown.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Wind of Change" do Scorpions saia com album "96% Puddel Rock" (2005), em vez
 * de "Crazy World" (1990). Duas causas, medidas na API real em 29/10/2026:
 *  - a busca devolvia 25 das 141 gravacoes (limite padrao) e nenhuma das 25
 *    trazia o Crazy World;
 *  - "96% Puddel Rock" e de "Various Artists" mas NAO tem secondary-type
 *    compilation no MusicBrainz — o filtro por tipo nao o pegava.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MusicBrainzColetaneaTest {

    /** Reduzido da resposta real; a data da coletanea foi antecipada para empatar o ano. */
    private val windOfChange = JSONArray(
        """
        [{ "id": "w1", "title": "Wind of Change",
           "artist-credit": [{"name": "Scorpions", "artist": {"id": "s", "name": "Scorpions"}}],
           "releases": [
             { "id": "va", "title": "96% Puddel Rock", "date": "1990",
               "artist-credit": [{"name": "Various Artists", "artist": {"id": "89ad4ac3-39f7-470e-963a-56509c546377", "name": "Various Artists"}}],
               "release-group": {"id": "rg-va", "primary-type": "Album"} },
             { "id": "cw", "title": "Crazy World", "date": "1990-11-06",
               "release-group": {"id": "rg-cw", "primary-type": "Album"} }
           ] }]
        """.trimIndent()
    )

    @Test
    fun `lancamento de Various Artists sem tipo coletanea nao vira album de origem`() {
        val escolha = MusicBrainzService.pickOriginalRecording(windOfChange, "Scorpions", "Wind of Change")

        assertEquals("Crazy World", escolha?.release?.optString("title"))
    }

    @Test
    fun `busca pede mais que as 25 gravacoes do limite padrao`() = runTest {
        val pedidas = mutableListOf<String>()
        val http = object : MusicBrainzHttpClient {
            override suspend fun get(url: String): HttpResponse {
                pedidas.add(url)
                return HttpResponse(200, """{"recordings": []}""")
            }
        }

        MusicBrainzService(http, StandardTestDispatcher(testScheduler)).searchRecording("Wind of Change", "Scorpions")

        assertTrue(pedidas.single().contains("limit=100"))
    }
}
