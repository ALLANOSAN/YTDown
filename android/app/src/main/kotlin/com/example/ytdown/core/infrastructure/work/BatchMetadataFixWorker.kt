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
        val songs = databaseService.getLibraryAudios()
        var successCount = 0
        var errorCount = 0

        for (song in songs) {
            try {
                val targetPath = song.exportedPath?.takeIf { it.isNotBlank() } ?: song.outputPath
                if (targetPath.isBlank() || !File(targetPath).exists()) {
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
                if (mbResult?.releaseId != null) {
                    val cacheKey = artworkCacheManager.getCacheKey(finalArtist, finalAlbum)
                    val cached = artworkCacheManager.getCachedAlbumArt(cacheKey)
                    if (cached != null) {
                        albumArtPath = cached.absolutePath
                    } else {
                        val bytes = coverArtService.downloadAlbumArt(mbResult.releaseId)
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
                android.util.Log.e("BatchMetadataFix", "❌ Erro ao corrigir ${song.title}: ${e.message}")
                errorCount++
            }
        }

        android.util.Log.d("BatchMetadataFix", "🏁 Concluído: $successCount sucessos, $errorCount erros")
        Result.success()
    }
}
