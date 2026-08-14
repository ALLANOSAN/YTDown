package com.example.ytdown.core.business

import com.example.ytdown.core.media.MediaImportProcessor
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.utils.MetadataUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.example.ytdown.utils.LocalLogger

/**
 * Botão "Reparar Tags" — reenriquece a biblioteca inteira.
 *
 * Delega ao MediaImportProcessor, que é o pipeline completo:
 *   MusicBrainz (título, artista, álbum, ano, faixa, disco)
 *   → Cover Art Archive / Last.fm (capa) → FanArt.tv (foto do artista)
 *   → Mutagen (grava tudo no arquivo) → banco.
 *
 * A implementação anterior usava enrich.py, que só devolve título/artista/álbum
 * — sem número de faixa, sem ano e sem os IDs necessários pra buscar a capa.
 */
@Singleton
class MetadataRepairer @Inject constructor(
    private val databaseService: DatabaseService,
    private val importProcessor: MediaImportProcessor
) {
    suspend fun repairAll(
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Int, Int> {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) return Triple(0, 0, 0)

        var repaired = 0
        var failed = 0
        var skipped = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Processando: ${item.title}")

            // SAF (content://) não dá pra reescrever por caminho direto
            val targetPath = item.outputPath
            if (targetPath.isBlank() || targetPath.startsWith("content://") || !File(targetPath).exists()) {
                failed++
                continue
            }

            // Pular so o que ja esta LIMPO. Checar apenas "campo preenchido"
            // deixava passar titulo que ainda e nome de arquivo, tipo
            // "02 Get Back To The Bible(m4a 128k)" — o proprio caso que este
            // botao existe para consertar.
            if (!MetadataUtils.needsMetadataRepair(
                    item.title, item.artist, item.album, item.albumArtPath != null
                )
            ) {
                LocalLogger.debug("Pulando ${item.title} — ja limpo e com capa", tag = "MetadataRepairer")
                skipped++
                continue
            }

            try {
                importProcessor.process(
                    audioPath = targetPath,
                    originalTitle = MetadataUtils.cleanFilenameTitle(item.title),
                    knownArtist = MetadataUtils.sanitizeArtist(item.artist).takeIf { it.isNotBlank() },
                    knownAlbum = item.album?.trim()?.takeUnless { MetadataUtils.isUnknownMetadata(it) },
                    forceEnrichment = true,
                    downloadId = item.id
                )
                repaired++
            } catch (e: Exception) {
                LocalLogger.error("Falha ao reparar ${item.title}: ${e.message}", tag = "MetadataRepairer")
                failed++
            }
        }
        return Triple(repaired, failed, skipped)
    }
}
