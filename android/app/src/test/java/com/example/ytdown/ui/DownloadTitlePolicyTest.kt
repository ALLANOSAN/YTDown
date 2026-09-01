package com.example.ytdown.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Numa playlist o campo de titulo vem pre-preenchido com o nome do ALBUM.
 * Aplicar esse titulo a todas as faixas dava o mesmo nome de arquivo pra
 * todas ("Petra - Beyond Belief - Beyond Belief"), e uma sobrescrevia a
 * outra na exportacao pro MediaStore.
 */
class DownloadTitlePolicyTest {

    private val faixasDoAlbum = listOf(
        "Petra - Armed and Dangerous",
        "Petra - I Am on the Rock",
        "Petra - Creed",
    )

    @Test
    fun `playlist mantem o titulo de cada faixa`() {
        val titulos = faixasDoAlbum.map { faixa ->
            DownloadTitlePolicy.resolveItemTitle(
                sharedTitle = "Beyond Belief",
                itemTitle = faixa,
                isPlaylist = true,
            )
        }

        assertEquals(faixasDoAlbum, titulos)
    }

    @Test
    fun `playlist nao pode gerar titulos repetidos`() {
        val titulos = faixasDoAlbum.map { faixa ->
            DownloadTitlePolicy.resolveItemTitle("Beyond Belief", faixa, isPlaylist = true)
        }

        assertEquals(
            "faixas com titulo repetido viram o mesmo arquivo e se sobrescrevem",
            faixasDoAlbum.size,
            titulos.toSet().size,
        )
        assertNotEquals("Beyond Belief", titulos.first())
    }

    @Test
    fun `video unico continua respeitando o titulo digitado`() {
        val titulo = DownloadTitlePolicy.resolveItemTitle(
            sharedTitle = "Meu Titulo Editado",
            itemTitle = "Armed and Dangerous",
            isPlaylist = false,
        )

        assertEquals("Meu Titulo Editado", titulo)
    }

    @Test
    fun `video unico com campo vazio cai no titulo do youtube`() {
        val titulo = DownloadTitlePolicy.resolveItemTitle(
            sharedTitle = "   ",
            itemTitle = "Armed and Dangerous",
            isPlaylist = false,
        )

        assertEquals("Armed and Dangerous", titulo)
    }

    @Test
    fun `sem titulo nenhum usa o fallback`() {
        val titulo = DownloadTitlePolicy.resolveItemTitle("", "", isPlaylist = true)

        assertEquals("Sem título", titulo)
    }
}
