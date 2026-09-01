package com.example.ytdown.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sob rate limit o MusicBrainz responde com um corpo de erro — e nem sempre com
 * status 5xx: a mesma query repetida devolveu 503 com corpo valido e 200 com
 * corpo de erro. O codigo antigo tratava os dois caminhos com `return null`,
 * indistinguivel de "banda nao existe", e sem retry. Era isso que fazia bandas
 * como o Whitecross aparecerem como "nao encontradas" de forma intermitente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MusicBrainzResponseTest {

    private val corpoOcupado =
        """{"error": "The MusicBrainz web server is currently busy. Please try again later."}"""

    @Test
    fun `corpo de erro com HTTP 200 e rate limit, nao ausencia de resultado`() {
        val resultado = MusicBrainzService.parseSearchResponse(corpoOcupado, 200)

        assertEquals(MusicBrainzService.SearchOutcome.RateLimited, resultado)
    }

    @Test
    fun `HTTP 503 e rate limit mesmo sem corpo aproveitavel`() {
        assertEquals(MusicBrainzService.SearchOutcome.RateLimited,
            MusicBrainzService.parseSearchResponse(null, 503))
    }

    @Test
    fun `busca sem resultado continua sendo Empty e nao vira retry`() {
        val semResultado =
            """{"created":"2026-08-31T20:40:00.128Z","count":0,"offset":0,"recordings":[]}"""

        assertEquals(MusicBrainzService.SearchOutcome.Empty,
            MusicBrainzService.parseSearchResponse(semResultado, 200))
    }

    /**
     * `OkHttpMusicBrainzClient` traduz queda de rede em codigo 0. Se a leitura
     * so tratasse 4xx/5xx como estrangulamento, o 0 cairia em Empty e a queda
     * de rede voltaria a significar "banda nao encontrada", sem retry.
     */
    @Test
    fun `codigo zero de falha de rede e rate limit, nao ausencia`() {
        assertEquals(MusicBrainzService.SearchOutcome.RateLimited,
            MusicBrainzService.parseSearchResponse(null, 0))
    }
}
