package com.example.ytdown.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O atalho do PASSO 0 tratava "capa no cache" como equivalente a "capa no
 * arquivo": bastava o cache ter a capa de (artista, album) para o pipeline
 * devolver cedo, gravando o caminho da capa no SongEntity e nunca chamando o
 * Mutagen. O player do app lia o banco e mostrava a capa; qualquer outro player
 * lia o arquivo e nao achava nada.
 *
 * Acontece na faixa 2..N de um album: a faixa 1 popula o cache com a mesma
 * chave e as seguintes pegam o atalho.
 *
 * A quebra que estes testes pegam: devolver NADA quando a capa esta so no cache
 * deixa o arquivo sem APIC para sempre.
 */
class ArtworkPolicyTest {

    @Test
    fun `capa so no cache precisa ser embutida no arquivo`() {
        assertEquals(
            AcaoDeCapa.EMBUTIR_DO_CACHE,
            ArtworkPolicy.decidir(
                tagsLimpas = true,
                capaNoArquivo = false,
                capaNoCache = true,
            )
        )
    }

    @Test
    fun `capa ja no arquivo dispensa qualquer trabalho`() {
        assertEquals(
            AcaoDeCapa.NADA,
            ArtworkPolicy.decidir(tagsLimpas = true, capaNoArquivo = true, capaNoCache = true)
        )
        assertEquals(
            AcaoDeCapa.NADA,
            ArtworkPolicy.decidir(tagsLimpas = true, capaNoArquivo = true, capaNoCache = false)
        )
    }

    @Test
    fun `sem capa em lugar nenhum roda o pipeline completo`() {
        assertEquals(
            AcaoDeCapa.ENRIQUECER,
            ArtworkPolicy.decidir(tagsLimpas = true, capaNoArquivo = false, capaNoCache = false)
        )
    }

    @Test
    fun `tag suja ignora o cache e roda o pipeline completo`() {
        assertEquals(
            AcaoDeCapa.ENRIQUECER,
            ArtworkPolicy.decidir(tagsLimpas = false, capaNoArquivo = true, capaNoCache = true)
        )
    }
}
