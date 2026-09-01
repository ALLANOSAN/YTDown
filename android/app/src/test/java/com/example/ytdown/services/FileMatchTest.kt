package com.example.ytdown.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O scanner religa itens da biblioteca aos arquivos nas pastas monitoradas e
 * grava o resultado em `exportedPath` — que e o alvo de TODA escrita posterior:
 * reparo de tags, renomear artista em lote, correcao de capa.
 *
 * O casamento era por `startsWith`, apesar do comentario dizer "match exato".
 * Um titulo que e prefixo de outro se liga ao arquivo da musica errada, e a
 * partir dai todas as gravacoes vao para o arquivo de outra faixa.
 */
class FileMatchTest {

    /**
     * A quebra que este teste pega: casar por prefixo liga "Behold" ao arquivo
     * de "Behold the Man", e o reparo de tags passa a reescrever a faixa errada.
     */
    @Test
    fun `titulo que e prefixo de outro nao rouba o arquivo do vizinho`() {
        val nomes = listOf("Behold the Man.m4a", "Behold.m4a", "Attention Please.m4a")

        assertEquals("Behold.m4a", FileMatch.escolher(nomes, "Behold", "m4a"))
    }

    /**
     * A quebra que este teste pega: manter o fallback por prefixo. Sem arquivo
     * exato, "Behold" se ligava a "Behold the Man.m4a" e o item passava a
     * apontar para a faixa de outra musica. Nao ligar e recuperavel; ligar
     * errado faz todo reparo seguinte reescrever o arquivo do vizinho.
     */
    @Test
    fun `sem arquivo exato prefere nao ligar a ligar no vizinho`() {
        val nomes = listOf("Behold the Man.m4a", "Attention Please.m4a")

        assertNull(FileMatch.escolher(nomes, "Behold", "m4a"))
    }

    /**
     * A quebra que este teste pega: comparar sem tolerar espaco em volta. O
     * titulo vem do banco e costuma carregar espaco de sobra do parser de nome
     * de arquivo; sem trim o item nunca reencontraria seu proprio arquivo.
     */
    @Test
    fun `espaco em volta do titulo nao impede o casamento`() {
        val nomes = listOf("Behold.m4a")

        assertEquals("Behold.m4a", FileMatch.escolher(nomes, "  Behold  ", "m4a"))
    }
}
