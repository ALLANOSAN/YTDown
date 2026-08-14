package com.example.ytdown.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A URL do Last.fm carrega a api_key no query string. Logar a URL inteira
 * escreve a chave em claro no logcat a cada busca de capa.
 */
class LastfmRedactionTest {

    @Test
    fun `esconde a api_key no meio da url`() {
        assertEquals(
            "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&api_key=REDACTED&format=json",
            LastfmService.redactSecrets(
                "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&api_key=deadbeefdeadbeefdeadbeefdeadbeef&format=json"
            )
        )
    }

    @Test
    fun `esconde a api_key no fim da url`() {
        assertEquals(
            "https://ws.audioscrobbler.com/2.0/?method=x&api_key=REDACTED",
            LastfmService.redactSecrets("https://ws.audioscrobbler.com/2.0/?method=x&api_key=abc123")
        )
    }

    @Test
    fun `url sem chave fica intacta`() {
        val url = "https://ws.audioscrobbler.com/2.0/?method=x&format=json"
        assertEquals(url, LastfmService.redactSecrets(url))
    }
}
