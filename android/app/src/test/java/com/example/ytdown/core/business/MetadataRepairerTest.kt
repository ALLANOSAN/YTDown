package com.example.ytdown.core.business

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * O botao "Reparar Tags" pulava todo item que parecesse limpo e tivesse capa.
 * Faixa gravada com album de coletanea cai exatamente nesse caso — "Heavy
 * Righteous Metal" e string limpa e o item tem capa (a da coletanea) — entao o
 * botao nunca a reparava, por mais vezes que fosse rodado.
 */
class MetadataRepairerTest {

    private class BibliotecaFake(private val lista: List<DownloadItemEntity>) : BibliotecaDeAudio {
        override suspend fun itens(): List<DownloadItemEntity> = lista
        override suspend fun atualizar(item: DownloadItemEntity) = Unit
    }

    private class LookupFake(private val resposta: MusicBrainzRecording?) : RecordingLookup {
        override suspend fun buscar(title: String, artist: String) = resposta
    }

    private class EnriquecedorFake : EnriquecedorDeItem {
        val enriquecidos = mutableListOf<String>()
        override suspend fun enriquecer(
            audioPath: String,
            title: String?,
            artist: String?,
            album: String?,
            downloadId: String?,
        ) {
            enriquecidos.add(title.orEmpty())
        }
    }

    private val arquivo: File = File.createTempFile("faixa", ".m4a").apply { deleteOnExit() }

    private fun item() = DownloadItemEntity(
        id = "1",
        url = "https://youtu.be/x",
        title = "Love on the Line",
        outputPath = arquivo.absolutePath,
        status = "completed",
        progress = 1.0,
        artist = "Whitecross",
        // Album de coletanea gravado pelo codigo antigo, que pegava releases[0].
        album = "Heavy Righteous Metal",
        albumArtPath = "/cache/capa-da-coletanea.jpg",
    )

    private val albumDeVerdade = MusicBrainzRecording(
        title = "Love on the Line",
        artist = "Whitecross",
        album = "Love on the Line",
        releaseId = "1e35af1d-3a76-4d9f-ac08-51589031276f",
        releaseGroupId = "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
        artistId = "51f24fe4-6cb7-477a-a6ed-6be19abf99bb",
    )

    /**
     * A quebra que este teste pega: o guard antigo (`needsMetadataRepair`) da o
     * item como pronto porque as strings estao limpas e ha capa. So a comparacao
     * com o album que o MusicBrainz devolve revela a coletanea.
     */
    @Test
    fun `item com album de coletanea e reparado em vez de pulado`() = runTest {
        val enriquecedor = EnriquecedorFake()

        val resumo = MetadataRepairer(
            BibliotecaFake(listOf(item())),
            LookupFake(albumDeVerdade),
            enriquecedor,
        ).repairAll { _, _ -> }

        assertEquals(listOf("Love on the Line"), enriquecedor.enriquecidos)
        assertEquals(1, resumo.repaired)
        assertEquals(0, resumo.skipped)
    }

    /**
     * A quebra que este teste pega: reparar a partir do silencio da API. Sob
     * rate limit — inevitavel numa varredura longa — isso reescreveria itens
     * sobre os quais nao se sabe nada.
     */
    @Test
    fun `MusicBrainz mudo preserva o item limpo`() = runTest {
        val enriquecedor = EnriquecedorFake()
        val jaCerto = item().copy(album = "Love on the Line")

        val resumo = MetadataRepairer(
            BibliotecaFake(listOf(jaCerto)),
            LookupFake(null),
            enriquecedor,
        ).repairAll { _, _ -> }

        assertEquals(0, enriquecedor.enriquecidos.size)
        assertEquals(1, resumo.skipped)
    }
}
