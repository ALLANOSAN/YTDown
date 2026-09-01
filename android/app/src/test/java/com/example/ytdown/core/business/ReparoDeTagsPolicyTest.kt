package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.AcaoDeReparo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O botao "Reparar Tags" pulava todo item que parecesse limpo e tivesse capa.
 * "Heavy Righteous Metal" e uma string limpa — nao e sentinela, nao e nome de
 * arquivo — e o item tinha capa (a da coletanea). Resultado: justamente as
 * faixas gravadas com album de coletanea nunca eram reparadas.
 *
 * Nao da para descobrir que um nome e coletanea olhando so a string; e preciso
 * perguntar ao MusicBrainz qual e o album de origem e comparar.
 */
class ReparoDeTagsPolicyTest {

    /**
     * A quebra que este teste pega: tratar "tem capa e parece limpo" como prova
     * de que o album esta certo deixa a coletanea gravada para sempre.
     */
    @Test
    fun `album divergente do MusicBrainz precisa de reparo mesmo parecendo limpo`() {
        assertEquals(
            AcaoDeReparo.REESCREVER,
            ReparoDeTagsPolicy.decidir(
                title = "Love on the Line",
                artist = "Whitecross",
                album = "Heavy Righteous Metal",
                temCapa = true,
                albumDoMusicBrainz = "Love on the Line",
            )
        )
    }

    /**
     * A quebra que este teste pega: decidir a partir do silencio da API. Numa
     * varredura de centenas de faixas a 1 req/s boa parte volta estrangulada;
     * tratar isso como "album diverge" reescreveria a biblioteca inteira em
     * cima de nada.
     */
    @Test
    fun `MusicBrainz mudo nao autoriza reparo de item limpo`() {
        assertEquals(
            AcaoDeReparo.SEM_FONTE,
            ReparoDeTagsPolicy.decidir(
                title = "Love on the Line",
                artist = "Whitecross",
                album = "Love on the Line",
                temCapa = true,
                albumDoMusicBrainz = null,
            )
        )
    }

    /**
     * A quebra que este teste pega: reparar item que ja esta certo. Cada reparo
     * custa duas requisicoes ao MusicBrainz e uma reescrita do arquivo; repetir
     * isso na biblioteca toda a cada varredura e trabalho puro.
     */
    @Test
    fun `album que confere com o MusicBrainz nao gera trabalho`() {
        assertEquals(
            AcaoDeReparo.NADA,
            ReparoDeTagsPolicy.decidir(
                title = "Love on the Line",
                artist = "Whitecross",
                album = "Love on the Line",
                temCapa = true,
                albumDoMusicBrainz = "Love on the Line",
            )
        )
    }

    /**
     * A quebra que este teste pega: passar a depender da rede para reparar o que
     * as strings ja denunciam. "Track 07 My Love" precisa de reparo mesmo com o
     * MusicBrainz mudo — o proprio enriquecimento refaz a busca com retry.
     */
    @Test
    fun `titulo sujo precisa de reparo mesmo sem resposta do MusicBrainz`() {
        assertEquals(
            AcaoDeReparo.REESCREVER,
            ReparoDeTagsPolicy.decidir(
                title = "Track 07 My Love",
                artist = "Whitecross",
                album = "Triumphant Return",
                temCapa = true,
                albumDoMusicBrainz = null,
            )
        )
    }

    /**
     * A quebra que este teste pega: exigir a rede para reparar item sem capa.
     * Sem capa o item precisa do pipeline de qualquer forma.
     */
    @Test
    fun `item sem capa precisa de reparo`() {
        assertEquals(
            AcaoDeReparo.REESCREVER,
            ReparoDeTagsPolicy.decidir(
                title = "Love on the Line",
                artist = "Whitecross",
                album = "Love on the Line",
                temCapa = false,
                albumDoMusicBrainz = "Love on the Line",
            )
        )
    }
}
