package com.example.ytdown.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A biblioteca carrega capas gravadas pelo codigo antigo, que escolhia
 * `releases[0]` e pegava coletanea: "Love on the Line" do Whitecross ficou com
 * a capa de "Heavy Righteous Metal". Essas capas nao dao para consertar pelo
 * cache — o cache e indexado por (artista, album) e guarda justamente a capa
 * errada sob o nome errado. So a rede sabe qual e o album de origem.
 *
 * O guard atual do MetadataRepairer nao pega esses itens: "Heavy Righteous
 * Metal" e uma string limpa e o item tem capa, entao `needsMetadataRepair`
 * devolve false e ele e pulado.
 */
class ReparoDeCapaPolicyTest {

    /**
     * A quebra que este teste pega: tratar "tem capa" como "capa certa" deixa
     * a capa de coletanea gravada para sempre.
     */
    @Test
    fun `album divergente do MusicBrainz significa capa de outro album`() {
        assertEquals(
            AcaoDeReparo.REESCREVER,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Heavy Righteous Metal",
                albumDoMusicBrainz = "Love on the Line",
                temCapa = true,
            )
        )
    }

    /**
     * A quebra que este teste pega: comparar as strings cruas. O MusicBrainz
     * mistura apostrofo U+2019 com ASCII e alterna caixa no catalogo da mesma
     * banda ("Enough Is Enough" / "Enough is Enough"). Com comparacao crua a
     * biblioteca inteira pareceria capa errada e seria reescrita a toa, contra
     * uma API limitada a 1 requisicao por segundo.
     */
    @Test
    fun `diferenca so de apostrofo ou caixa nao e capa errada`() {
        assertEquals(
            AcaoDeReparo.NADA,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "It's My Life",
                albumDoMusicBrainz = "It\u2019s My Life",
                temCapa = true,
            )
        )
        assertEquals(
            AcaoDeReparo.NADA,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Enough Is Enough",
                albumDoMusicBrainz = "Enough is Enough",
                temCapa = true,
            )
        )
    }

    /**
     * A quebra que este teste pega: tratar "a API nao respondeu" como decisao.
     * Numa varredura de centenas de faixas o rate limit vai estrangular parte
     * das buscas; concluir alguma coisa a partir do silencio da API significaria
     * mexer na capa de itens sobre os quais nao se sabe nada.
     */
    @Test
    fun `MusicBrainz sem resposta nao autoriza mexer na capa`() {
        assertEquals(
            AcaoDeReparo.SEM_FONTE,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Love on the Line",
                albumDoMusicBrainz = null,
                temCapa = false,
            )
        )
        assertEquals(
            AcaoDeReparo.SEM_FONTE,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Love on the Line",
                albumDoMusicBrainz = null,
                temCapa = true,
            )
        )
    }

    /**
     * A quebra que este teste pega: colapsar "capa certa" e "sem capa" num
     * unico desfecho. O item cujo album confere mas que perdeu a capa precisa
     * de busca, nao de ser pulado.
     */
    @Test
    fun `album confere mas sem capa e caso de buscar`() {
        assertEquals(
            AcaoDeReparo.BUSCAR,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Love on the Line",
                albumDoMusicBrainz = "Love on the Line",
                temCapa = false,
            )
        )
    }

    @Test
    fun `album confere e com capa nao gera trabalho`() {
        assertEquals(
            AcaoDeReparo.NADA,
            ReparoDeCapaPolicy.decidir(
                albumAtual = "Love on the Line",
                albumDoMusicBrainz = "Love on the Line",
                temCapa = true,
            )
        )
    }
}
