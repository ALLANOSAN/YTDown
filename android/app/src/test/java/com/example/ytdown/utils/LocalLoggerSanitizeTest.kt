package com.example.ytdown.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A redação é o que impede URL e caminho do dispositivo de vazarem para o
 * Crashlytics. Estava duplicada em `LocalLogger` e `ObservabilityService` com
 * regex ligeiramente diferentes — duas cópias de uma regra de privacidade é
 * uma cópia a mais.
 */
class LocalLoggerSanitizeTest {

    @Test
    fun `remove url`() {
        assertEquals(
            "falha ao baixar [URL_REMOVIDA]",
            LocalLogger.sanitize("falha ao baixar https://coverartarchive.org/release/abc/front")
        )
        assertEquals("[URL_REMOVIDA]", LocalLogger.sanitize("http://exemplo.com"))
    }

    @Test
    fun `remove caminho privado do app`() {
        assertEquals(
            "sem espaco em [PATH_SISTEMA_REMOVIDO]",
            LocalLogger.sanitize("sem espaco em /data/user/0/com.example.ytdown/files/cookies.txt")
        )
    }

    @Test
    fun `remove caminho de armazenamento externo`() {
        assertEquals(
            "erro em [PATH_SISTEMA_REMOVIDO]",
            LocalLogger.sanitize("erro em /storage/emulated/0/Music/YTDown/song.mp3")
        )
    }

    @Test
    fun `nao vaza api key embutida em url`() {
        val out = LocalLogger.sanitize(
            "GET https://ws.audioscrobbler.com/2.0/?api_key=deadbeefdeadbeefdeadbeefdeadbeef"
        )
        assertFalse("chave vazou: $out", out.contains("deadbeefdeadbeefdeadbeefdeadbeef"))
    }

    @Test
    fun `mensagem sem dado sensivel fica intacta`() {
        assertEquals("MusicBrainz sem resultados", LocalLogger.sanitize("MusicBrainz sem resultados"))
        assertEquals("", LocalLogger.sanitize(""))
    }
}
