package com.example.ytdown.core.business

import com.example.ytdown.core.domain.AlbumName
import com.example.ytdown.core.domain.ArtistName
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renomeacao em lote de artista e album.
 *
 * Estava dentro do LibraryRepository, junto de favoritos, playlists e buscas
 * recentes, e por isso dependia de tres DAOs que estas duas operacoes nao usam —
 * o que tornava o comportamento inverificavel.
 */
@Singleton
class LibraryRenamer @Inject constructor(
    private val downloadDao: DownloadDao,
    private val tagRewriter: TagRewriter,
) {

    /**
     * Atualiza o nome e a foto de um ARTISTA em todos os seus arquivos.
     */
    suspend fun updateArtistInBatch(
        oldName: String,
        newName: String,
        localPhotoPath: String?
    ) {
        val allDownloads = downloadDao.getAllDownloadsSync()
        val artistTracks = allDownloads.filter { it.artist?.equals(oldName, ignoreCase = true) == true }

        artistTracks.forEach { track ->
            val updated = track.copy(artist = newName, artistArtPath = localPhotoPath ?: track.artistArtPath)
            downloadDao.update(updated)
            
            // Regrava a tag física no arquivo, incluindo artwork se uma imagem da galeria foi selecionada.
            val targetPath = track.exportedPath?.takeIf { it.isNotBlank() } ?: track.outputPath
            tagRewriter.reescrever(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    title = MediaTitle(track.title),
                    artist = ArtistName(newName),
                    album = AlbumName(track.album ?: "")
                ),
                exportedPath = track.exportedPath,
                // A foto escolhida aqui e arte de ARTISTA. Passada como
                // artworkUrl ela entra no frame APIC — a capa do album — e
                // substitui a capa do disco em todas as faixas do artista de
                // uma vez. `null` significa "nao mexe na capa" para o Mutagen.
                artworkUrl = null
            )
        }
    }

    /**
     * Atualiza o nome e a foto de um ÁLBUM em todos os seus arquivos.
     * Mantém intactos os campos de ARTISTA para esses arquivos.
     */
    suspend fun updateAlbumInBatch(
        artist: String? = null,
        oldAlbum: String,
        newAlbum: String,
        localPhotoPath: String?
    ) {
        val allDownloads = downloadDao.getAllDownloadsSync()
        val albumTracks = allDownloads.filter {
            it.album?.equals(oldAlbum, ignoreCase = true) == true &&
            (artist.isNullOrBlank() || it.artist?.equals(artist, ignoreCase = true) == true)
        }

        albumTracks.forEach { track ->
            val updated = track.copy(album = newAlbum, albumArtPath = localPhotoPath ?: track.albumArtPath)
            downloadDao.update(updated)
            
            // Regrava a tag física no arquivo, incluindo artwork se uma imagem da galeria foi selecionada.
            val targetPath = track.exportedPath?.takeIf { it.isNotBlank() } ?: track.outputPath
            tagRewriter.reescrever(
                path = FilePath(targetPath),
                metadata = MediaMetadata(
                    title = MediaTitle(track.title),
                    artist = ArtistName(track.artist ?: ""),
                    album = AlbumName(newAlbum)
                ),
                exportedPath = track.exportedPath,
                artworkUrl = localPhotoPath
            )
        }
    }
}
