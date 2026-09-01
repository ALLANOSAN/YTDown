package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.FanArtTvService
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import com.example.ytdown.services.CoverArtArchiveService
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.LastfmService
import com.example.ytdown.services.MusicBrainzService
import com.example.ytdown.utils.LocalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Fronteiras do enriquecimento de capas.
 *
 * O ArtworkEnricher dependia direto de sete colaboradores concretos, entre eles
 * o PythonMetadataBridge (que chama `Python.getInstance()` do Chaquopy e nao
 * roda em unit test). Com isso a varredura inteira era inverificavel — inclusive
 * o caso em que ela regravava a capa errada. Cada porta abaixo isola uma coisa
 * externa: rede, rede de imagem, e escrita de tag pelo Python.
 */

/** Busca no MusicBrainz. */
interface RecordingLookup {
    suspend fun buscar(title: String, artist: String): MusicBrainzRecording?
}

/** De onde vem imagem: Cover Art Archive, Last.fm/iTunes/Deezer e FanArt.tv. */
interface CoverSource {
    suspend fun capaDoRelease(releaseGroupId: String?, releaseId: String?): ByteArray?
    suspend fun capaAlternativa(artist: String, album: String, title: String): ByteArray?
    suspend fun fotoDoArtista(artistId: String): ByteArray?
}

/** Escrita de tag no arquivo (Mutagen, via Chaquopy). */
interface TagWriter {
    suspend fun gravar(
        path: String,
        title: String,
        artist: String,
        album: String,
        year: String?,
        albumArt: String?,
        trackNumber: String?,
        discNumber: String?,
    ): Boolean
}

/** Itens de audio da biblioteca. */
interface BibliotecaDeAudio {
    suspend fun itens(): List<DownloadItemEntity>
    suspend fun atualizar(item: DownloadItemEntity)
}

// --- Implementacoes de producao ---

class RecordingLookupMusicBrainz @Inject constructor(
    private val service: MusicBrainzService,
) : RecordingLookup {
    override suspend fun buscar(title: String, artist: String) =
        service.searchRecording(title, artist)
}

class CoverSourcePadrao @Inject constructor(
    private val coverArtArchive: CoverArtArchiveService,
    private val lastfm: LastfmService,
    private val fanArt: FanArtTvService,
) : CoverSource {

    override suspend fun capaDoRelease(releaseGroupId: String?, releaseId: String?): ByteArray? =
        when {
            releaseGroupId != null -> coverArtArchive.downloadAlbumArt(releaseGroupId, releaseId)
            releaseId != null -> coverArtArchive.downloadAlbumArt(releaseId)
            else -> null
        }

    override suspend fun capaAlternativa(
        artist: String,
        album: String,
        title: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val url = (if (album.isNotBlank()) lastfm.getAlbumCover(artist, album) else null)
            ?: lastfm.getTrackCover(artist, title)
            ?: return@withContext null
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (Linux; Android 14) YTDown/1.0"
            )
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doInput = true
            conn.connect()
            conn.inputStream.readBytes().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            LocalLogger.error("Erro ao baixar capa alternativa: ${e.message}", tag = "CoverSource")
            null
        }
    }

    override suspend fun fotoDoArtista(artistId: String): ByteArray? =
        fanArt.downloadArtistImage(artistId)
}

class TagWriterMutagen @Inject constructor(
    private val bridge: PythonMetadataBridge,
) : TagWriter {
    override suspend fun gravar(
        path: String,
        title: String,
        artist: String,
        album: String,
        year: String?,
        albumArt: String?,
        trackNumber: String?,
        discNumber: String?,
    ): Boolean = bridge.writeFullMetadata(
        path = path,
        title = title,
        artist = artist,
        album = album,
        year = year,
        albumArt = albumArt,
        trackNumber = trackNumber,
        discNumber = discNumber,
    )
}

class BibliotecaDeAudioRoom @Inject constructor(
    private val databaseService: DatabaseService,
) : BibliotecaDeAudio {
    override suspend fun itens() = databaseService.getLibraryAudios()
    override suspend fun atualizar(item: DownloadItemEntity) =
        databaseService.updateDownload(item)
}

/** Enriquecimento completo de um item (MusicBrainz + capa + Mutagen + banco). */
interface EnriquecedorDeItem {
    suspend fun enriquecer(
        audioPath: String,
        title: String?,
        artist: String?,
        album: String?,
        downloadId: String?,
    )
}

class EnriquecedorViaImportProcessor @Inject constructor(
    private val processor: com.example.ytdown.core.media.MediaImportProcessor,
) : EnriquecedorDeItem {
    override suspend fun enriquecer(
        audioPath: String,
        title: String?,
        artist: String?,
        album: String?,
        downloadId: String?,
    ) = processor.process(
        audioPath = audioPath,
        originalTitle = title,
        knownArtist = artist,
        knownAlbum = album,
        forceEnrichment = true,
        downloadId = downloadId,
    )
}
