package com.example.ytdown.core.business

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.SongEntity
import com.example.ytdown.utils.MetadataUtils

/**
 * Ponte entre o resultado do enrichment (SongEntity, chaveado por caminho) e o
 * que a biblioteca mostra (DownloadItemEntity).
 *
 * Era código duplicado em FileSystemScannerService, DownloadWorker e
 * MetadataRepairer, e as três cópias copiavam campo vazio por cima de dado bom.
 */
object LibrarySync {

    /**
     * Aplica os metadados enriquecidos sobre o item da biblioteca.
     * Campo vazio ou sentinela do enrichment nunca sobrescreve o que já existe.
     */
    fun merge(download: DownloadItemEntity, song: SongEntity): DownloadItemEntity =
        download.copy(
            title = song.title.takeUnless { MetadataUtils.isUnknownMetadata(it) } ?: download.title,
            artist = song.artist.takeUnless { MetadataUtils.isUnknownMetadata(it) } ?: download.artist,
            album = song.album.takeUnless { MetadataUtils.isUnknownMetadata(it) } ?: download.album,
            albumArtPath = song.albumArtwork ?: download.albumArtPath,
            artistArtPath = song.artistArtwork ?: download.artistArtPath
        )
}
