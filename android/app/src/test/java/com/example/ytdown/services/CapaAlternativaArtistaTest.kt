package com.example.ytdown.services

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Visto no aparelho (29/10/2026): 18 faixas do Fruto Sagrado, baixadas de um
 * canal chamado "David Carvalho", ficaram com a capa de um disco de forro de
 * "Adriano Carvalho". iTunes e Deezer fazem busca textual e devolvem o mais
 * parecido; o codigo pegava o primeiro resultado com capa sem olhar o artista.
 */
class CapaAlternativaArtistaTest {

    private val itunes = JsonParser.parseString(
        """{"results": [
          {"artistName": "Adriano Carvalho", "artworkUrl100": "https://x/forro/100x100bb.jpg"},
          {"artistName": "Fruto Sagrado", "artworkUrl100": "https://x/segredo/100x100bb.jpg"}
        ]}"""
    ).asJsonObject

    @Test
    fun `iTunes pula resultado de outro artista`() {
        assertEquals(
            "https://x/segredo/300x300bb.jpg",
            LastfmService.capaDoItunes(itunes, "Fruto Sagrado"),
        )
    }

    @Test
    fun `iTunes sem resultado do artista nao devolve capa`() {
        assertNull(LastfmService.capaDoItunes(itunes, "David Carvalho"))
    }

    private val deezer = JsonParser.parseString(
        """{"data": [
          {"artist": {"name": "Adriano Carvalho"}, "album": {"cover_big": "https://d/forro.jpg"}},
          {"artist": {"name": "Petra"}, "album": {"cover_big": "https://d/this-means-war.jpg"}}
        ]}"""
    ).asJsonObject

    @Test
    fun `Deezer pula faixa de outro artista`() {
        assertEquals("https://d/this-means-war.jpg", LastfmService.capaDoDeezer(deezer, "Petra"))
    }

    @Test
    fun `Deezer sem resultado do artista nao devolve capa`() {
        assertNull(LastfmService.capaDoDeezer(deezer, "David Carvalho"))
    }
}
