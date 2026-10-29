package com.example.ytdown.core.business

import com.example.ytdown.core.domain.AlbumName
import com.example.ytdown.core.domain.ArtistName
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.DownloadType
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.MediaTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtro de indisponíveis antes do enfileiramento (par do probe Python
 * check_availability). Decisão pura, sem I/O — segue o padrão das policies
 * de artwork (ArtworkPolicy/ReparoDeTagsPolicy): o mapa de disponibilidade
 * entra pronto, a policy só decide quem agenda.
 *
 * Regra de segurança: URL sem resultado no mapa (probe inconclusivo) SEMPRE
 * agenda — nunca se descarta vídeo por falta de informação.
 */
class UnavailableSkipPolicyTest {

    private fun spec(url: String) = PlaylistItem(
        url = url,
        meta = MediaMetadata(
            MediaTitle("titulo"),
            ArtistName("artista"),
            AlbumName("album")
        ),
        options = DownloadOptions(
            type = DownloadType.AUDIO,
            format = "m4a",
            quality = "128"
        )
    )

    @Test
    fun `indisponiveis vao para ignorados e disponiveis para agendar`() {
        val itens = listOf(spec("url-boa-1"), spec("url-ruim"), spec("url-boa-2"))
        val disponibilidade = mapOf(
            "url-boa-1" to true,
            "url-ruim" to false,
            "url-boa-2" to true
        )

        val particao = UnavailableSkipPolicy.partir(itens, disponibilidade)

        assertEquals(listOf("url-boa-1", "url-boa-2"), particao.agendar.map { it.url })
        assertEquals(listOf("url-ruim"), particao.ignorados.map { it.url })
    }

    @Test
    fun `url sem resultado no mapa agenda por seguranca`() {
        val itens = listOf(spec("url-sem-sonda"))

        val particao = UnavailableSkipPolicy.partir(itens, emptyMap())

        assertEquals(listOf("url-sem-sonda"), particao.agendar.map { it.url })
        assertTrue(particao.ignorados.isEmpty())
    }

    @Test
    fun `lista vazia parte em listas vazias`() {
        val particao = UnavailableSkipPolicy.partir(emptyList(), emptyMap())

        assertTrue(particao.agendar.isEmpty())
        assertTrue(particao.ignorados.isEmpty())
    }

    @Test
    fun `todos indisponiveis esvazia agendar`() {
        val itens = listOf(spec("url-ruim-1"), spec("url-ruim-2"))
        val disponibilidade = mapOf("url-ruim-1" to false, "url-ruim-2" to false)

        val particao = UnavailableSkipPolicy.partir(itens, disponibilidade)

        assertTrue(particao.agendar.isEmpty())
        assertEquals(2, particao.ignorados.size)
    }
}
