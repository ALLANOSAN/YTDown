package com.example.ytdown.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.services.CoverArtArchiveService
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.MusicBrainzService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.ytdown.utils.LocalLogger

/**
 * Botão "Adicionar ID3" — Enriquecimento completo de metadados via MusicBrainz.
 *
 * Fluxo: MusicBrainz → Cover Art Archive → Mutagen (grava no arquivo)
 * NÃO baixa foto do artista (FanArt.tv).
 */
@HiltWorker
class BatchMetadataFixWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseService: DatabaseService,
    private val musicBrainzService: MusicBrainzService,
    private val coverArtService: CoverArtArchiveService,
    private val artworkCacheManager: ArtworkCacheManager,
    private val pythonMetadataBridge: PythonMetadataBridge
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songs = alvos(databaseService.getLibraryAudios(), inputData.getString("TARGET_ID"))
        var successCount = 0
        var errorCount = 0

        for (song in songs) {
            try {
                val targetPath = caminhoParaReparo(song)
                if (targetPath == null) {
                    android.util.Log.w("BatchMetadataFix", "⚠️ Arquivo não encontrado: ${song.title}")
                    errorCount++
                    continue
                }

                val searchTitle = song.title.trim()
                val searchArtist = song.artist?.trim() ?: ""

                android.util.Log.d("BatchMetadataFix", "🔍 Buscando MusicBrainz: $searchArtist - $searchTitle")

                // 1. MusicBrainz — metadados completos (ano, faixa, álbum, releaseId)
                val mbResult = musicBrainzService.searchRecording(searchTitle, searchArtist)

                val finalTitle = mbResult?.title ?: searchTitle
                val finalArtist = mbResult?.artist ?: searchArtist.ifBlank { "Artista Desconhecido" }
                val finalAlbum = mbResult?.album ?: song.album ?: "Álbum Desconhecido"
                val year = mbResult?.year
                val trackNumber = mbResult?.trackNumber

                // 2. Cover Art Archive — capa do álbum (cache + arquivo)
                var albumArtPath: String? = null
                if (mbResult?.releaseGroupId != null || mbResult?.releaseId != null) {
                    val cacheKey = artworkCacheManager.chaveDaCapa(finalArtist, finalAlbum, finalTitle)
                    val cached = artworkCacheManager.getCachedAlbumArt(cacheKey)
                    if (cached != null) {
                        albumArtPath = cached.absolutePath
                    } else {
                        val bytes = if (mbResult?.releaseGroupId != null) {
                            coverArtService.downloadAlbumArt(mbResult.releaseGroupId, mbResult.releaseId)
                        } else {
                            coverArtService.downloadAlbumArt(mbResult?.releaseId!!)
                        }
                        if (bytes != null) {
                            val saved = artworkCacheManager.saveToAlbumCache(cacheKey, bytes)
                            albumArtPath = saved?.absolutePath
                        }
                    }
                }

                // 3. Mutagen — gravar metadados + capa no arquivo
                pythonMetadataBridge.writeFullMetadata(
                    path = targetPath,
                    title = finalTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    year = year,
                    albumArt = albumArtPath,
                    trackNumber = trackNumber
                )

                // 4. Atualizar banco de dados (SEM foto do artista)
                val updatedSong = song.copy(
                    title = finalTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    albumArtPath = albumArtPath ?: song.albumArtPath
                )
                databaseService.updateDownload(updatedSong)

                android.util.Log.d("BatchMetadataFix", "✅ ID3 atualizado: $finalArtist - $finalTitle (ano=$year, faixa=$trackNumber)")
                successCount++
            } catch (e: Exception) {
                LocalLogger.error("❌ Erro ao corrigir ${song.title}: ${e.message}", tag = "BatchMetadataFix")
                errorCount++
            }
        }

        android.util.Log.d("BatchMetadataFix", "🏁 Concluído: $successCount sucessos, $errorCount erros")
        Result.success()
    }

    companion object {
        /**
         * Recorte da biblioteca para o reparo: com alvo, so a faixa dele;
         * sem alvo, a biblioteca inteira (botoes "reparar tudo"); alvo
         * desconhecido nao repara nada em vez de varrer tudo por engano.
         */
        fun alvos(
            biblioteca: List<com.example.ytdown.core.domain.DownloadItemEntity>,
            targetId: String?,
        ): List<com.example.ytdown.core.domain.DownloadItemEntity> {
            if (targetId.isNullOrBlank()) return biblioteca
            return biblioteca.filter { it.id == targetId }
        }

        /**
         * Arquivo que o Mutagen consegue regravar. Download concluido tem
         * `exportedPath` em `content://` (MediaStore): testar so ele com
         * `File.exists()` dava "nao encontrado" para toda musica baixada.
         */
        fun caminhoParaReparo(
            song: com.example.ytdown.core.domain.DownloadItemEntity,
            // isFile, nao exists: o outputPath de download exportado e a pasta.
            existe: (String) -> Boolean = { File(it).isFile },
        ): String? =
            (com.example.ytdown.core.business.RepairTarget.resolver(song.outputPath, song.exportedPath, existe)
                as? com.example.ytdown.core.business.RepairTarget.Arquivo)?.path
    }
}
