package com.example.ytdown.core.business

import androidx.paging.PagingSource
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Renomear um artista permite escolher uma foto da galeria. Essa foto e arte de
 * ARTISTA: ela vai para o `artistArtPath` no banco e para o cache, nunca para o
 * arquivo de audio. Gravada la, ela entra no frame APIC — que e a capa do album
 * — e todo player passa a mostrar a foto da banda no lugar do disco, em TODAS
 * as faixas daquele artista de uma vez.
 */
class LibraryRenameTest {

    private class DaoFake(private var itens: List<DownloadItemEntity>) : DownloadDao {
        val atualizados = mutableListOf<DownloadItemEntity>()
        override suspend fun getAllDownloadsSync(): List<DownloadItemEntity> = itens
        override suspend fun update(item: DownloadItemEntity) {
            atualizados.add(item)
        }

        override fun getAllDownloads(): Flow<List<DownloadItemEntity>> = TODO()
        override fun getDownloadsPaged(): PagingSource<Int, DownloadItemEntity> = TODO()
        override suspend fun upsert(item: DownloadItemEntity) = TODO()
        override suspend fun delete(item: DownloadItemEntity) = TODO()
        override suspend fun getById(id: String): DownloadItemEntity? = TODO()
        override suspend fun getNextPending(): DownloadItemEntity? = TODO()
        override suspend fun resetStuckDownloading() = TODO()
        override fun getRecentlyAdded(limit: Int): Flow<List<DownloadItemEntity>> = TODO()
        override fun getDistinctArtists(): Flow<List<String>> = TODO()
        override fun getDistinctAlbums(): Flow<List<String>> = TODO()
        override fun searchLibrary(query: String): Flow<List<DownloadItemEntity>> = TODO()
    }

    private class ReescritorFake : TagRewriter {
        data class Chamada(val caminho: String, val artista: String, val artworkUrl: String?)

        val chamadas = mutableListOf<Chamada>()
        override suspend fun reescrever(
            path: FilePath,
            metadata: MediaMetadata,
            exportedPath: String?,
            artworkUrl: String?,
        ): ExitCode {
            chamadas.add(Chamada(path.value, metadata.artist.value, artworkUrl))
            return ExitCode(0)
        }
    }

    private fun faixa(id: String, titulo: String) = DownloadItemEntity(
        id = id,
        url = "https://youtu.be/$id",
        title = titulo,
        outputPath = "/musica/$titulo.m4a",
        status = "completed",
        progress = 1.0,
        artist = "Whitecross",
        album = "Triumphant Return",
        albumArtPath = "/cache/capa-do-disco.jpg",
    )

    /**
     * A quebra que este teste pega: passar a foto do artista como `artworkUrl`
     * grava ela no APIC e destroi a capa do album de todas as faixas.
     */
    @Test
    fun `renomear artista com foto da galeria nao toca na capa do album`() = runTest {
        val dao = DaoFake(listOf(faixa("1", "Attention Please"), faixa("2", "Behold")))
        val reescritor = ReescritorFake()

        LibraryRenamer(dao, reescritor)
            .updateArtistInBatch("Whitecross", "White Cross", "/galeria/foto-da-banda.jpg")

        assertEquals(2, reescritor.chamadas.size)
        reescritor.chamadas.forEach {
            assertNull(
                "a foto do artista nao pode virar capa do album",
                it.artworkUrl,
            )
            assertEquals("White Cross", it.artista)
        }
    }

    /**
     * A quebra que este teste pega: deixar de guardar a foto no banco. Ela some
     * do cache de artista e a tela do artista volta a ficar sem imagem.
     */
    @Test
    fun `a foto escolhida continua indo para o campo de arte do artista`() = runTest {
        val dao = DaoFake(listOf(faixa("1", "Attention Please")))

        LibraryRenamer(dao, ReescritorFake())
            .updateArtistInBatch("Whitecross", "White Cross", "/galeria/foto-da-banda.jpg")

        val atualizado = dao.atualizados.single()
        assertEquals("/galeria/foto-da-banda.jpg", atualizado.artistArtPath)
        assertEquals("a capa do disco tem de ficar como estava", "/cache/capa-do-disco.jpg", atualizado.albumArtPath)
    }
}
