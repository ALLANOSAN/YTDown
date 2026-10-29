package com.example.ytdown.core.business

/**
 * Quando o titulo de uma playlist do YouTube e nome de album.
 *
 * So a playlist de album do YouTube Music (id `OLAK5uy_...`) e um disco. Usar
 * o titulo de qualquer playlist como album gravou "As melhores" — playlist de
 * usuario — em 19 faixas do Racionais, e o MusicBrainz nao corrige quando nao
 * acha a faixa.
 */
object AlbumDaPlaylist {

    private const val PREFIXO_ALBUM_YT_MUSIC = "OLAK5uy_"
    private val listParam = Regex("""[?&]list=([^&#]+)""")
    private val prefixoDeTitulo = Regex("""^Album\s+-\s+""", RegexOption.IGNORE_CASE)

    fun decidir(playlistId: String?, url: String, playlistTitle: String?): String? {
        val id = playlistId?.takeIf { it.isNotBlank() } ?: listParam.find(url)?.groupValues?.get(1)
        if (id == null || !id.startsWith(PREFIXO_ALBUM_YT_MUSIC)) return null
        return playlistTitle?.replace(prefixoDeTitulo, "")?.trim()?.ifBlank { null }
    }
}
