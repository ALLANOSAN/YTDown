package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.AcaoDeReparo
import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.artwork.ReparoDeCapaPolicy
import com.example.ytdown.utils.LocalLogger
import com.example.ytdown.utils.MetadataUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Botão "Capas" — reconsulta o MusicBrainz item a item, baixa a capa do álbum
 * de origem e grava no arquivo (Mutagen) + cache. A foto do artista vai só para
 * o cache, nunca para o arquivo.
 *
 * Depende de portas, não dos serviços concretos: o antigo acoplamento com o
 * PythonMetadataBridge (Chaquopy) tornava a varredura inteira inverificável.
 */
@Singleton
class ArtworkEnricher @Inject constructor(
    private val biblioteca: BibliotecaDeAudio,
    private val lookup: RecordingLookup,
    private val capas: CoverSource,
    private val escritor: TagWriter,
    private val cache: ArtworkCacheManager,
) {

    suspend fun enrichAll(onProgress: (Float, String) -> Unit): ArtworkSummary {
        val items = biblioteca.itens()
        var atualizados = 0
        var falhas = 0
        var pulados = 0
        var processados = 0

        for (item in items) {
            processados++
            onProgress(processados / items.size.toFloat(), "Capas: ${item.title}")

            // Antes de qualquer rede: arquivo que sumiu do disco nao pode
            // gastar requisicao de uma API limitada a 1 por segundo.
            val caminho = item.exportedPath?.takeIf { it.isNotBlank() } ?: item.outputPath
            if (caminho.isBlank() || !File(caminho).exists()) {
                falhas++
                continue
            }

            try {
                val knownArtist = MetadataUtils.sanitizeArtist(item.artist)
                var mbResult = lookup.buscar(item.title.trim(), knownArtist)

                // O artista gravado no arquivo costuma divergir do MusicBrainz.
                // A busca so por titulo recupera o caso, mas o resultado so vale
                // se o artista bater — homonimo de outra banda traria a capa do
                // disco errado. Comparacao normalizada: apostrofo tipografico e
                // caixa rejeitariam o match certo.
                if (mbResult == null && knownArtist.isNotBlank()) {
                    val porTitulo = lookup.buscar(item.title.trim(), "")
                    if (porTitulo != null &&
                        MetadataUtils.normalizeForMatch(knownArtist) ==
                        MetadataUtils.normalizeForMatch(porTitulo.artist)
                    ) {
                        mbResult = porTitulo
                    }
                }

                // Sem resposta do MusicBrainz nao da para decidir nada sobre a capa.
                // Seguir em frente montava a chave de cache com o album ANTIGO — o
                // da coletanea — acertava a entrada envenenada e regravava a capa
                // errada no arquivo, contando como sucesso.
                if (ReparoDeCapaPolicy.decidir(
                        albumAtual = item.album,
                        albumDoMusicBrainz = mbResult?.album,
                        temCapa = item.albumArtPath != null,
                    ) == AcaoDeReparo.SEM_FONTE
                ) {
                    LocalLogger.debug(
                        "MusicBrainz sem resposta para ${item.title} — capa preservada",
                        tag = "ArtworkEnricher"
                    )
                    pulados++
                    continue
                }

                // MusicBrainz e a fonte de verdade; o banco so entra quando ele falha.
                // Usar o album do banco aqui manteria a chave do cache apontando
                // para a capa da coletanea.
                val artist = MetadataUtils.sanitizeArtist(mbResult?.artist)
                    .ifBlank { knownArtist }
                val album = mbResult?.album?.trim()?.ifBlank { null }
                    ?: item.album?.trim().orEmpty()

                val chave = cache.getCacheKey(artist, album)
                val albumArtPath = cache.getCachedAlbumArt(chave)?.absolutePath
                    ?: (
                        capas.capaDoRelease(mbResult?.releaseGroupId, mbResult?.releaseId)
                            // O CAA nao cobre boa parte do catalogo; sem este
                            // fallback esses discos ficariam sem capa nenhuma.
                            ?: capas.capaAlternativa(artist, album, item.title.trim())
                        )?.let { cache.saveToAlbumCache(chave, it).absolutePath }

                // Nenhuma fonte devolveu capa: nao ha o que gravar nem o que
                // atualizar. Contar isso como sucesso inflava o numero da tela de
                // ajustes e escondia que a varredura nao achou nada.
                if (albumArtPath == null) {
                    pulados++
                    continue
                }

                // Arte de artista, nao capa: vai para o cache e para o banco, nunca
                // para o APIC do arquivo — embutida la substituiria a capa do disco.
                val artistArtPath = mbResult?.artistId?.let { artistId ->
                    val chaveArtista = cache.getArtistCacheKey(artist)
                    cache.getCachedArtistArt(chaveArtista)?.absolutePath
                        ?: capas.fotoDoArtista(artistId)
                            ?.let { cache.saveToArtistCache(chaveArtista, it).absolutePath }
                }

                val targetPath = caminho
                val finalTitle = mbResult?.title?.trim()?.ifBlank { null } ?: item.title.trim()

                if (albumArtPath != null) {
                    escritor.gravar(
                        path = targetPath,
                        title = finalTitle,
                        artist = artist,
                        album = album,
                        year = mbResult?.year,
                        albumArt = albumArtPath,
                        trackNumber = mbResult?.trackNumber,
                        discNumber = mbResult?.discNumber,
                    )
                }

                // O que foi gravado no arquivo e o que a biblioteca mostra nao
                // podem divergir.
                biblioteca.atualizar(
                    item.copy(
                        title = if (albumArtPath != null) finalTitle else item.title,
                        artist = artist,
                        album = album.ifBlank { item.album.orEmpty() },
                        albumArtPath = albumArtPath ?: item.albumArtPath,
                        artistArtPath = artistArtPath ?: item.artistArtPath,
                    )
                )
                atualizados++
            } catch (e: Exception) {
                // Um item problematico nao pode abortar a varredura:
                // sem isto a primeira faixa ruim impedia o processamento
                // de todas as seguintes.
                LocalLogger.error(
                    "Erro nas capas de ${item.title}: ${e.message}", e, "ArtworkEnricher"
                )
                falhas++
            }
        }
        return ArtworkSummary(atualizados, falhas, pulados)
    }
}

/** Desfechos da varredura de capas. A ordem alimenta o destructuring no ViewModel. */
data class ArtworkSummary(
    val atualizados: Int,
    val falhas: Int,
    val pulados: Int,
)
