package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Botao "Recuperar arquivos": conserta o que bugs antigos deixaram na biblioteca.
 *  - m4a com tag ID3 colada na frente (19 no aparelho, 29/10/2026);
 *  - capa compartilhada pela chave "artista sem album" — o Fruto Sagrado com a
 *    capa de forro, o Petra com a capa do Beyond Belief.
 * O "Reparar Tags" nao pega esses casos: titulo, artista e album parecem limpos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RecuperadorDeBibliotecaTest {

    private class BibliotecaFake(val lista: List<DownloadItemEntity>) : BibliotecaDeAudio {
        val atualizados = mutableListOf<DownloadItemEntity>()
        override suspend fun itens() = lista
        override suspend fun atualizar(item: DownloadItemEntity) { atualizados.add(item) }
    }

    private class EnriquecedorFake : EnriquecedorDeItem {
        val chamadas = mutableListOf<Pair<String, String?>>()
        override suspend fun enriquecer(audioPath: String, title: String?, artist: String?, album: String?, downloadId: String?) {
            chamadas.add(audioPath to downloadId)
        }
    }

    private class LeitorFake(val porCaminho: Map<String, ByteArray>) : LeitorDeCabecalho {
        override suspend fun ler(caminho: String, bytes: Int) = porCaminho[caminho]
    }

    private val m4aSadio = byteArrayOf(0, 0, 0, 0x1c) + "ftypM4A ".toByteArray() + ByteArray(20)
    private val m4aEstragado = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 0, 10) + ByteArray(10) + m4aSadio

    private lateinit var cache: ArtworkCacheManager
    private lateinit var pastaDeCapas: File

    @Before
    fun preparar() {
        val ctx: android.content.Context = RuntimeEnvironment.getApplication()
        cache = ArtworkCacheManager(ctx)
        pastaDeCapas = File(ctx.cacheDir, "artwork/albums")
    }

    private fun faixa(id: String, title: String = "Faixa $id", album: String? = "Disco", capa: String? = null) =
        DownloadItemEntity(
            id = id, url = "", title = title, status = "completed", progress = 1.0,
            outputPath = "/storage/Music/YTDown", exportedPath = "content://media/external_primary/audio/media/$id",
            artist = "Fruto Sagrado", album = album, albumArtPath = capa,
        )

    private fun recuperador(b: BibliotecaFake, e: EnriquecedorFake, l: LeitorFake) =
        RecuperadorDeBiblioteca(b, e, l, cache)

    @Test
    fun `so o m4a estragado passa pelo enriquecimento, pelo endereco content`() = runTest {
        val biblioteca = BibliotecaFake(listOf(faixa("1"), faixa("2")))
        val enriquecedor = EnriquecedorFake()
        val leitor = LeitorFake(mapOf(
            "content://media/external_primary/audio/media/1" to m4aEstragado,
            "content://media/external_primary/audio/media/2" to m4aSadio,
        ))

        val resumo = recuperador(biblioteca, enriquecedor, leitor).recuperar { _, _ -> }

        assertEquals(listOf("content://media/external_primary/audio/media/1" to "1"), enriquecedor.chamadas)
        assertEquals(1, resumo.arquivosRecuperados)
    }

    @Test
    fun `capa da chave artista sem album e descartada antes de buscar outra`() = runTest {
        val contaminada = File(pastaDeCapas, cache.getCacheKey("David Carvalho", "") + ".jpg").absolutePath
        val item = faixa("1", title = "Desesperado", album = "YTDown", capa = contaminada)
        val biblioteca = BibliotecaFake(listOf(item))
        val enriquecedor = EnriquecedorFake()

        val resumo = recuperador(biblioteca, enriquecedor, LeitorFake(mapOf(item.exportedPath!! to m4aSadio)))
            .recuperar { _, _ -> }

        assertNull(biblioteca.atualizados.single().albumArtPath)
        assertEquals(1, enriquecedor.chamadas.size)
        assertEquals(1, resumo.capasDescartadas)
    }

    @Test
    fun `capa por faixa e capa de disco conhecido ficam como estao`() = runTest {
        val daFaixa = File(pastaDeCapas, cache.chaveDaCapa("Fruto Sagrado", "YTDown", "Desesperado") + ".jpg").absolutePath
        val doDisco = File(pastaDeCapas, cache.getCacheKey("Fruto Sagrado", "O Segredo") + ".jpg").absolutePath
        val biblioteca = BibliotecaFake(listOf(
            faixa("1", title = "Desesperado", album = "YTDown", capa = daFaixa),
            faixa("2", album = "O Segredo", capa = doDisco),
        ))
        val enriquecedor = EnriquecedorFake()
        val leitor = LeitorFake(biblioteca.lista.associate { it.exportedPath!! to m4aSadio })

        recuperador(biblioteca, enriquecedor, leitor).recuperar { _, _ -> }

        assertTrue(enriquecedor.chamadas.isEmpty())
        assertTrue(biblioteca.atualizados.isEmpty())
    }
}
