package com.example.ytdown.core.business

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "0 de 626 · 626 falharam": o guard de caminho do MetadataRepairer olhava
 * SÓ o outputPath (o ArtworkEnricher, no mesmo cenario, usa
 * exportedPath ?: outputPath) e jogava TODOS os casos em `failed`, sem log.
 *
 * Item exportado para SAF nao e falha, e arquivo sumido nao e o mesmo que
 * excecao no enriquecimento — misturar os tres esconde a causa.
 */
class RepairTargetTest {

    private val existeSempre: (String) -> Boolean = { true }
    private val naoExiste: (String) -> Boolean = { false }

    @Test
    fun `prefere exportedPath quando existe`() {
        assertEquals(
            RepairTarget.Arquivo("/musica/exportada.mp3"),
            RepairTarget.resolver("/interno/a.mp3", "/musica/exportada.mp3", existeSempre)
        )
    }

    @Test
    fun `cai para outputPath quando exportedPath vazio ou nulo`() {
        assertEquals(
            RepairTarget.Arquivo("/interno/a.mp3"),
            RepairTarget.resolver("/interno/a.mp3", null, existeSempre)
        )
        assertEquals(
            RepairTarget.Arquivo("/interno/a.mp3"),
            RepairTarget.resolver("/interno/a.mp3", "   ", existeSempre)
        )
    }

    /** SAF nao e falha: e caminho nao suportado por reescrita direta. */
    @Test
    fun `content uri e classificado como SAF`() {
        assertEquals(
            RepairTarget.Saf,
            RepairTarget.resolver("content://tree/x", null, existeSempre)
        )
        // So e SAF quando NENHUM caminho comum serve — se o interno existisse,
        // o caso abaixo (`exportado em SAF com arquivo interno`) manda usar ele.
        assertEquals(
            RepairTarget.Saf,
            RepairTarget.resolver("/interno/a.mp3", "content://tree/x", naoExiste)
        )
        assertEquals(
            RepairTarget.Saf,
            RepairTarget.resolver("content://tree/a", "content://tree/b", existeSempre)
        )
    }

    /** Se o exportado e SAF mas o interno existe, da para reparar pelo interno. */
    @Test
    fun `exportado em SAF com arquivo interno usa o interno`() {
        assertEquals(
            RepairTarget.Arquivo("/interno/a.mp3"),
            RepairTarget.resolver("/interno/a.mp3", "content://tree/x") { it == "/interno/a.mp3" }
        )
    }

    @Test
    fun `arquivo inexistente e sem caminho sao SemArquivo`() {
        assertEquals(RepairTarget.SemArquivo, RepairTarget.resolver("/sumiu.mp3", null, naoExiste))
        assertEquals(RepairTarget.SemArquivo, RepairTarget.resolver("", null, existeSempre))
        assertEquals(RepairTarget.SemArquivo, RepairTarget.resolver("   ", "", existeSempre))
    }
}
