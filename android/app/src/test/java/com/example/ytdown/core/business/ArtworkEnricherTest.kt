package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
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
 * O "botao Capas" reconsulta o MusicBrainz item a item e regrava a capa no
 * arquivo. As tres coisas externas — MusicBrainz, fontes de capa e a escrita de
 * tag pelo Chaquopy — entram por porta; o cache de artwork e o REAL, porque e
 * so sistema de arquivos e o Robolectric fornece um cacheDir de verdade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArtworkEnricherTest {

    private class BibliotecaFake(
        var itens: MutableList<DownloadItemEntity>,
    ) : BibliotecaDeAudio {
        val atualizados = mutableListOf<DownloadItemEntity>()
        override suspend fun itens(): List<DownloadItemEntity> = itens
        override suspend fun atualizar(item: DownloadItemEntity) {
            atualizados.add(item)
        }
    }

    private class LookupFake(private val porTitulo: Map<String, MusicBrainzRecording?>) :
        RecordingLookup {
        val consultas = mutableListOf<Pair<String, String>>()
        override suspend fun buscar(title: String, artist: String): MusicBrainzRecording? {
            consultas.add(title to artist)
            return porTitulo[title]
        }
    }

    private class FonteDeCapaFake(
        private val porRelease: ByteArray? = null,
        private val alternativa: ByteArray? = null,
        private val artista: ByteArray? = null,
    ) : CoverSource {
        override suspend fun capaDoRelease(releaseGroupId: String?, releaseId: String?) = porRelease
        override suspend fun capaAlternativa(artist: String, album: String, title: String) =
            alternativa
        override suspend fun fotoDoArtista(artistId: String): ByteArray? = artista
    }

    private class EscritorFake : TagWriter {
        val gravacoes = mutableListOf<Triple<String, String, String?>>()
        override suspend fun gravar(
            path: String,
            title: String,
            artist: String,
            album: String,
            year: String?,
            albumArt: String?,
            trackNumber: String?,
            discNumber: String?,
        ): Boolean {
            gravacoes.add(Triple(path, album, albumArt))
            return true
        }
    }

    private lateinit var cache: ArtworkCacheManager
    private lateinit var arquivoDeAudio: File

    private val capaCerta = byteArrayOf(1, 2, 3, 4)
    private val capaDaColetanea = byteArrayOf(9, 9, 9, 9)

    @Before
    fun preparar() {
        val ctx: android.content.Context = RuntimeEnvironment.getApplication()
        cache = ArtworkCacheManager(ctx)
        arquivoDeAudio = File(ctx.cacheDir, "faixa.m4a").apply { writeBytes(byteArrayOf(0)) }
    }

    private fun item() = DownloadItemEntity(
        id = "1",
        url = "https://youtu.be/x",
        title = "Love on the Line",
        status = "completed",
        progress = 1.0,
        outputPath = arquivoDeAudio.absolutePath,
        artist = "Whitecross",
        // Album errado gravado pelo codigo antigo, que pegava releases[0].
        album = "Heavy Righteous Metal",
        albumArtPath = "/cache/antigo.jpg",
    )

    private fun enricher(
        biblioteca: BibliotecaDeAudio,
        lookup: RecordingLookup,
        capas: CoverSource,
        escritor: TagWriter,
    ) = ArtworkEnricher(biblioteca, lookup, capas, escritor, cache)

    /**
     * A quebra que este teste pega: seguir em frente sem resposta do MusicBrainz
     * monta a chave de cache com o album ANTIGO, acerta a entrada envenenada e
     * regrava a capa da coletanea no arquivo, contando como sucesso. Numa
     * varredura de centenas de itens a 1 req/s isso acontece muitas vezes.
     */
    @Test
    fun `MusicBrainz mudo nao regrava a capa antiga do cache`() = runTest {
        // Cache envenenado: capa da coletanea sob a chave do album errado.
        cache.saveToAlbumCache(
            cache.getCacheKey("Whitecross", "Heavy Righteous Metal"), capaDaColetanea
        )
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()

        val resultado = enricher(
            biblioteca,
            LookupFake(mapOf("Love on the Line" to null)),
            FonteDeCapaFake(),
            escritor,
        ).enrichAll { _, _ -> }

        assertEquals("nada pode ter sido gravado no arquivo", 0, escritor.gravacoes.size)
        assertEquals("o item nao pode contar como atualizado", 0, resultado.atualizados)
        assertEquals(1, resultado.pulados)
    }

    /**
     * A quebra que este teste pega: usar o album do banco em vez do album que o
     * MusicBrainz devolve faz a chave do cache continuar apontando para a capa
     * da coletanea, mesmo com a busca respondendo certo.
     */
    @Test
    fun `album corrigido pelo MusicBrainz grava a capa nova no arquivo`() = runTest {
        cache.saveToAlbumCache(
            cache.getCacheKey("Whitecross", "Heavy Righteous Metal"), capaDaColetanea
        )
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val mb = MusicBrainzRecording(
            title = "Love on the Line",
            artist = "Whitecross",
            album = "Love on the Line",
            releaseId = "1e35af1d-3a76-4d9f-ac08-51589031276f",
            releaseGroupId = "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
            artistId = "51f24fe4-6cb7-477a-a6ed-6be19abf99bb",
        )

        val resultado = enricher(
            biblioteca,
            LookupFake(mapOf("Love on the Line" to mb)),
            FonteDeCapaFake(porRelease = capaCerta),
            escritor,
        ).enrichAll { _, _ -> }

        assertEquals(1, escritor.gravacoes.size)
        val (caminho, album, capa) = escritor.gravacoes.single()
        assertEquals(arquivoDeAudio.absolutePath, caminho)
        assertEquals("Love on the Line", album)
        assertTrue("a capa gravada tem de ser a nova", capa != null)
        assertEquals(
            capaCerta.toList(),
            File(capa!!).readBytes().toList(),
        )
        assertEquals(1, resultado.atualizados)
        assertEquals("Love on the Line", biblioteca.atualizados.single().album)
    }

    /**
     * A quebra que este teste pega: contar como sucesso um item para o qual
     * nenhuma fonte devolveu capa. O contador e o unico retorno que a tela de
     * ajustes mostra — inflado, ele esconde que a varredura nao achou nada.
     */
    @Test
    fun `sem capa em nenhuma fonte o arquivo nao e tocado`() = runTest {
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val mb = MusicBrainzRecording(
            title = "Love on the Line",
            artist = "Whitecross",
            album = "Love on the Line",
            releaseId = null,
            releaseGroupId = null,
            artistId = null,
        )

        val lookup = LookupFake(mapOf("Love on the Line" to mb))

        val resultado = enricher(
            biblioteca,
            lookup,
            FonteDeCapaFake(porRelease = null, alternativa = null),
            escritor,
        ).enrichAll { _, _ -> }

        assertEquals(0, escritor.gravacoes.size)
        assertEquals(0, resultado.atualizados)
        // Sem estas duas, um enrichAll que nao faz nada tambem passaria: e
        // preciso provar que o item foi consultado e classificado como pulado,
        // nao simplesmente ignorado.
        assertEquals(1, lookup.consultas.size)
        assertEquals(1, resultado.pulados)
        assertNull(biblioteca.atualizados.firstOrNull()?.albumArtPath)
    }

    /**
     * A quebra que este teste pega: parar no Cover Art Archive. Boa parte do
     * catalogo cristao dos anos 80 nao tem capa no CAA, e sem o fallback esses
     * discos ficariam sem capa nenhuma mesmo com o album identificado.
     */
    @Test
    fun `cai para a fonte alternativa quando o Cover Art Archive nao tem`() = runTest {
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val mb = MusicBrainzRecording(
            title = "Love on the Line",
            artist = "Whitecross",
            album = "Love on the Line",
            releaseId = null,
            releaseGroupId = null,
            artistId = null,
        )

        enricher(
            biblioteca,
            LookupFake(mapOf("Love on the Line" to mb)),
            FonteDeCapaFake(porRelease = null, alternativa = capaCerta),
            escritor,
        ).enrichAll { _, _ -> }

        val capa = escritor.gravacoes.single().third
        assertEquals(capaCerta.toList(), File(capa!!).readBytes().toList())
    }

    /**
     * A quebra que este teste pega: gravar a foto do artista dentro do arquivo
     * de audio. Ela e arte de artista, nao capa de album — embutida no APIC ela
     * substituiria a capa e todo player mostraria a foto da banda no lugar do
     * disco. Vai so para o cache e para o banco.
     */
    @Test
    fun `foto do artista vai para o banco mas nunca para o arquivo`() = runTest {
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val fotoDaBanda = byteArrayOf(7, 7, 7)
        val mb = MusicBrainzRecording(
            title = "Love on the Line",
            artist = "Whitecross",
            album = "Love on the Line",
            releaseId = null,
            releaseGroupId = "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
            artistId = "51f24fe4-6cb7-477a-a6ed-6be19abf99bb",
        )

        enricher(
            biblioteca,
            LookupFake(mapOf("Love on the Line" to mb)),
            FonteDeCapaFake(porRelease = capaCerta, artista = fotoDaBanda),
            escritor,
        ).enrichAll { _, _ -> }

        val gravadoNoArquivo = File(escritor.gravacoes.single().third!!).readBytes().toList()
        assertEquals("o arquivo tem de receber a capa, nao a foto", capaCerta.toList(), gravadoNoArquivo)

        val fotoNoBanco = biblioteca.atualizados.single().artistArtPath
        assertEquals(fotoDaBanda.toList(), File(fotoNoBanco!!).readBytes().toList())
    }

    /**
     * A quebra que este teste pega: tratar arquivo sumido como item normal.
     * Alem de contar errado, gastaria uma requisicao do MusicBrainz — limitado
     * a 1 por segundo — num arquivo que nao existe mais.
     */
    @Test
    fun `arquivo que sumiu do disco conta como falha e nao consulta a rede`() = runTest {
        val sumido = item().copy(outputPath = "/nao/existe/faixa.m4a")
        val biblioteca = BibliotecaFake(mutableListOf(sumido))
        val lookup = LookupFake(emptyMap())
        val escritor = EscritorFake()

        val resultado = enricher(biblioteca, lookup, FonteDeCapaFake(), escritor)
            .enrichAll { _, _ -> }

        assertEquals(1, resultado.falhas)
        assertEquals(0, resultado.pulados)
        assertEquals("nao pode gastar requisicao com arquivo sumido", 0, lookup.consultas.size)
    }

    /**
     * A quebra que este teste pega: deixar a excecao de um item subir. Numa
     * varredura de centenas de faixas isso aborta tudo no primeiro arquivo
     * problematico e as faixas seguintes nunca sao processadas.
     */
    @Test
    fun `falha em um item nao interrompe a varredura`() = runTest {
        val bom = item().copy(id = "2", title = "Enough Is Enough")
        val biblioteca = BibliotecaFake(mutableListOf(item(), bom))
        val escritor = EscritorFake()
        val mb = MusicBrainzRecording(
            title = "Enough Is Enough",
            artist = "Whitecross",
            album = "Whitecross",
            releaseId = null,
            releaseGroupId = "d5db8fca-4f67-309d-a75a-57686f28e819",
            artistId = null,
        )
        val lookup = object : RecordingLookup {
            override suspend fun buscar(title: String, artist: String): MusicBrainzRecording? {
                if (title == "Love on the Line") error("MusicBrainz devolveu lixo")
                return mb
            }
        }

        val resultado = enricher(biblioteca, lookup, FonteDeCapaFake(porRelease = capaCerta), escritor)
            .enrichAll { _, _ -> }

        assertEquals(1, resultado.falhas)
        assertEquals("a faixa seguinte tem de ser processada", 1, resultado.atualizados)
        assertEquals("Whitecross", escritor.gravacoes.single().second)
    }

    /**
     * A quebra que este teste pega: desistir quando a busca com artista nao
     * acha. O artista gravado no arquivo costuma divergir do MusicBrainz
     * ("Whitecross " com espaco, grafia alternativa), e a busca so por titulo
     * recupera o caso. O guard de artista impede aceitar musica homonima de
     * outra banda — e compara normalizado, senao apostrofo tipografico e caixa
     * rejeitariam o match certo.
     */
    @Test
    fun `sem resultado com artista, busca so por titulo e confere o artista`() = runTest {
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val mb = MusicBrainzRecording(
            title = "Love on the Line",
            // Caixa diferente de proposito: o guard tem de aceitar.
            artist = "WHITECROSS",
            album = "Love on the Line",
            releaseId = null,
            releaseGroupId = "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
            artistId = null,
        )
        val lookup = object : RecordingLookup {
            val consultas = mutableListOf<Pair<String, String>>()
            override suspend fun buscar(title: String, artist: String): MusicBrainzRecording? {
                consultas.add(title to artist)
                return if (artist.isBlank()) mb else null
            }
        }

        val resultado = enricher(biblioteca, lookup, FonteDeCapaFake(porRelease = capaCerta), escritor)
            .enrichAll { _, _ -> }

        assertEquals(listOf("Whitecross", ""), lookup.consultas.map { it.second })
        assertEquals(1, resultado.atualizados)
        assertEquals("Love on the Line", escritor.gravacoes.single().second)
    }

    /**
     * A quebra que este teste pega: aceitar o resultado da busca por titulo sem
     * conferir o artista. "Love on the Line" existe em varias bandas — sem o
     * guard o arquivo receberia a capa do disco de outro artista.
     */
    @Test
    fun `busca so por titulo e recusada quando o artista diverge`() = runTest {
        val biblioteca = BibliotecaFake(mutableListOf(item()))
        val escritor = EscritorFake()
        val deOutraBanda = MusicBrainzRecording(
            title = "Love on the Line",
            artist = "Def Leppard",
            album = "High'n'Dry",
            releaseId = null,
            releaseGroupId = "outro-grupo",
            artistId = null,
        )
        val lookup = object : RecordingLookup {
            override suspend fun buscar(title: String, artist: String): MusicBrainzRecording? =
                if (artist.isBlank()) deOutraBanda else null
        }

        val resultado = enricher(biblioteca, lookup, FonteDeCapaFake(porRelease = capaCerta), escritor)
            .enrichAll { _, _ -> }

        assertEquals("nada pode ser gravado a partir de outra banda", 0, escritor.gravacoes.size)
        assertEquals(1, resultado.pulados)
    }
}
