package com.example.ytdown.core.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Todo download de playlist recebia o titulo da playlist como album. 19
 * faixas do Racionais ficaram no album "As melhores" — nome de uma playlist
 * de usuario, nao de disco. So a playlist de album do YouTube Music (id
 * OLAK5uy_...) e de fato um album.
 */
class AlbumDaPlaylistTest {

    @Test
    fun `playlist de usuario nao vira album`() {
        assertNull(AlbumDaPlaylist.decidir("PLx8aB3kQ", "https://youtube.com/playlist?list=PLx8aB3kQ", "As melhores"))
    }

    @Test
    fun `playlist de album do YouTube Music vira album`() {
        assertEquals(
            "Sobrevivendo no Inferno",
            AlbumDaPlaylist.decidir("OLAK5uy_nHikntf2j7", "https://music.youtube.com/playlist?list=OLAK5uy_nHikntf2j7", "Sobrevivendo no Inferno"),
        )
    }

    /** O YouTube Music titula a playlist de album como "Album - Nome". */
    @Test
    fun `prefixo Album do YouTube Music sai do nome`() {
        assertEquals("Crazy World", AlbumDaPlaylist.decidir(null, "https://www.youtube.com/watch?v=x&list=OLAK5uy_abc", "Album - Crazy World"))
    }
}
