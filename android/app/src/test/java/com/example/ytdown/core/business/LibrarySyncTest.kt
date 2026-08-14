package com.example.ytdown.core.business

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O enrichment grava SongEntity; a biblioteca lê DownloadItemEntity. A cópia
 * de um pro outro estava duplicada em FileSystemScannerService, DownloadWorker
 * e (agora) MetadataRepairer — e copiava campo vazio por cima de dado bom.
 */
class LibrarySyncTest {

    private fun download(
        title: String = "Titulo Antigo",
        artist: String? = "Artista Antigo",
        album: String? = "Album Antigo",
        albumArtPath: String? = null,
        artistArtPath: String? = null
    ) = DownloadItemEntity(
        id = "id1", url = "", title = title, outputPath = "/x.mp3",
        status = "completed", progress = 1.0, artist = artist, album = album,
        albumArtPath = albumArtPath, artistArtPath = artistArtPath
    )

    private fun song(
        title: String = "Titulo Novo",
        artist: String = "Artista Novo",
        album: String = "Album Novo",
        albumArtwork: String? = null,
        artistArtwork: String? = null
    ) = SongEntity(
        path = "/x.mp3", title = title, artist = artist, album = album,
        duration = 0L, albumArtwork = albumArtwork, artistArtwork = artistArtwork
    )

    @Test
    fun `usa os metadados enriquecidos`() {
        val merged = LibrarySync.merge(download(), song())
        assertEquals("Titulo Novo", merged.title)
        assertEquals("Artista Novo", merged.artist)
        assertEquals("Album Novo", merged.album)
    }

    @Test
    fun `nao apaga dado bom com campo vazio do enrichment`() {
        val merged = LibrarySync.merge(download(), song(title = "", artist = "", album = ""))
        assertEquals("Titulo Antigo", merged.title)
        assertEquals("Artista Antigo", merged.artist)
        assertEquals("Album Antigo", merged.album)
    }

    @Test
    fun `nao propaga sentinela do enrichment`() {
        val merged = LibrarySync.merge(
            download(),
            song(title = "Unknown", artist = "Desconhecido", album = "YTDown")
        )
        assertEquals("Titulo Antigo", merged.title)
        assertEquals("Artista Antigo", merged.artist)
        assertEquals("Album Antigo", merged.album)
    }

    @Test
    fun `capa nova entra e capa antiga e preservada quando nao ha nova`() {
        val comCapa = LibrarySync.merge(
            download(albumArtPath = "/velha.jpg", artistArtPath = "/velho.jpg"),
            song(albumArtwork = "/nova.jpg", artistArtwork = "/novo.jpg")
        )
        assertEquals("/nova.jpg", comCapa.albumArtPath)
        assertEquals("/novo.jpg", comCapa.artistArtPath)

        val semCapa = LibrarySync.merge(
            download(albumArtPath = "/velha.jpg", artistArtPath = "/velho.jpg"),
            song()
        )
        assertEquals("/velha.jpg", semCapa.albumArtPath)
        assertEquals("/velho.jpg", semCapa.artistArtPath)
    }

    @Test
    fun `nao mexe nos demais campos`() {
        val original = download()
        val merged = LibrarySync.merge(original, song())
        assertEquals(original.id, merged.id)
        assertEquals(original.outputPath, merged.outputPath)
        assertEquals(original.status, merged.status)
        assertEquals(original.createdAt, merged.createdAt)
    }

    @Test
    fun `enrichment vazio sobre banco vazio nao inventa valor`() {
        val merged = LibrarySync.merge(
            download(artist = null, album = null),
            song(artist = "", album = "")
        )
        assertEquals(null, merged.artist)
        assertEquals(null, merged.album)
    }
}
