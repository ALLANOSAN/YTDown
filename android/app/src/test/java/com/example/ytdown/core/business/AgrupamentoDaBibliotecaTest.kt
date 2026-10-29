package com.example.ytdown.core.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lista agrupa pela chave e o detalhe filtra pela chave: se os dois lados
 * não concordarem, a faixa some da tela ou o clique quebra a navegação.
 */
class AgrupamentoDaBibliotecaTest {

    @Test
    fun `nulo vazio e literal null viram Desconhecido`() {
        assertEquals("Desconhecido", AgrupamentoDaBiblioteca.chave(null))
        assertEquals("Desconhecido", AgrupamentoDaBiblioteca.chave(""))
        assertEquals("Desconhecido", AgrupamentoDaBiblioteca.chave("   "))
        assertEquals("Desconhecido", AgrupamentoDaBiblioteca.chave("null"))
        assertEquals("Desconhecido", AgrupamentoDaBiblioteca.chave("NULL"))
    }

    @Test
    fun `nome legitimo passa intacto inclusive com barra`() {
        assertEquals("Petra", AgrupamentoDaBiblioteca.chave("Petra"))
        assertEquals(
            "Big City Nights / Holiday",
            AgrupamentoDaBiblioteca.chave("Big City Nights / Holiday")
        )
    }

    @Test
    fun `faixa de artista null mora no grupo Desconhecido`() {
        assertTrue(AgrupamentoDaBiblioteca.pertenceAoGrupo(null, "Desconhecido"))
        assertTrue(AgrupamentoDaBiblioteca.pertenceAoGrupo("null", "Desconhecido"))
        assertTrue(AgrupamentoDaBiblioteca.pertenceAoGrupo("Petra", "Petra"))
    }

    @Test
    fun `faixa nao invade o grupo alheio`() {
        assertFalse(AgrupamentoDaBiblioteca.pertenceAoGrupo("Petra", "Desconhecido"))
        assertFalse(AgrupamentoDaBiblioteca.pertenceAoGrupo("Petra", "Scorpions"))
        assertFalse(AgrupamentoDaBiblioteca.pertenceAoGrupo(null, "Petra"))
    }
}
