package com.example.ytdown.core.infrastructure.work

import com.example.ytdown.core.domain.DownloadItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O `superFixID3(song)` manda o `TARGET_ID` da faixa, mas o worker ignorava e
 * varria a biblioteca inteira — nao havia como testar o reparo numa faixa so
 * (ex.: uma do Blackout) sem reprocessar tudo.
 */
class BatchMetadataFixWorkerTest {

    private fun faixa(id: String) = DownloadItemEntity(
        id = id,
        url = "",
        title = "Faixa $id",
        outputPath = "/tmp/$id.mp3",
        status = "completed",
        progress = 1.0,
    )

    private val biblioteca = listOf(faixa("1"), faixa("2"), faixa("3"))

    @Test
    fun `com TARGET_ID repara so a faixa alvo`() {
        val alvos = BatchMetadataFixWorker.alvos(biblioteca, "2")

        assertEquals(listOf("2"), alvos.map { it.id })
    }

    @Test
    fun `sem TARGET_ID repara a biblioteca inteira`() {
        val alvos = BatchMetadataFixWorker.alvos(biblioteca, null)

        assertEquals(listOf("1", "2", "3"), alvos.map { it.id })
    }

    @Test
    fun `TARGET_ID desconhecido nao repara nada`() {
        val alvos = BatchMetadataFixWorker.alvos(biblioteca, "inexistente")

        assertTrue(alvos.isEmpty())
    }

    /**
     * Download concluido grava `exportedPath` como URI do MediaStore. O worker
     * fazia `File(exportedPath).exists()` — sempre falso para `content://` —
     * e contava erro sem tentar o `outputPath`, que existe. O botao
     * "Correcao Inteligente" nao corrigia nenhuma musica baixada pelo app.
     */
    @Test
    fun `exportado em content usa o arquivo interno que existe`() {
        val baixada = faixa("1").copy(exportedPath = "content://media/external/audio/media/42")

        val caminho = BatchMetadataFixWorker.caminhoParaReparo(baixada) { it == "/tmp/1.mp3" }

        assertEquals("/tmp/1.mp3", caminho)
    }

    @Test
    fun `sem arquivo regravavel nao devolve caminho`() {
        val sumiu = faixa("1").copy(exportedPath = "content://media/external/audio/media/42")

        assertNull(BatchMetadataFixWorker.caminhoParaReparo(sumiu) { false })
    }

    /**
     * Visto no aparelho (29/10/2026): os downloads exportados ao MediaStore
     * guardam em `outputPath` a PASTA de destino, e ela existe (vazia).
     * `File.exists()` aceitava o diretorio e o Mutagen tentava gravar nele.
     */
    @Test
    fun `pasta no outputPath nao e arquivo regravavel`() {
        val pasta = kotlin.io.path.createTempDirectory("YTDown").toFile()
        val exportada = faixa("1").copy(
            outputPath = pasta.absolutePath,
            exportedPath = "content://media/external_primary/audio/media/97972",
        )

        assertNull(BatchMetadataFixWorker.caminhoParaReparo(exportada))
        pasta.delete()
    }
}
