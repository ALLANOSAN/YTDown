package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.AcaoDeReparo
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
    private val biblioteca: BibliotecaDeAudio,
    private val lookup: RecordingLookup,
    private val enriquecedor: EnriquecedorDeItem,
) {
    suspend fun repairAll(
        onProgress: (Float, String) -> Unit
    ): RepairSummary {
        val items = biblioteca.itens()
        if (items.isEmpty()) return RepairSummary(0, 0, 0, 0)

        var repaired = 0
        var failed = 0
        var skipped = 0
        var semArquivo = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Processando: ${item.title}")

            // SAF agora e suportado: o MediaImportProcessor copia para temp,
            // enriquece e devolve. Sem isso, biblioteca adicionada pelo seletor
            // de pastas do Android era 100% recusada ("626 falharam").
            val targetPath = when (
                val alvo = RepairTarget.resolver(item.outputPath, item.exportedPath) {
                    it.startsWith("content://") || File(it).exists()
                }
            ) {
                is RepairTarget.Arquivo -> alvo.path
                RepairTarget.Saf -> item.exportedPath?.takeIf { it.startsWith("content://") }
                    ?: item.outputPath
                RepairTarget.SemArquivo -> {
                    LocalLogger.debug(
                        "Arquivo inexistente: ${item.title} (out=${item.outputPath}, " +
                            "exp=${item.exportedPath})",
                        tag = "MetadataRepairer"
                    )
                    semArquivo++
                    continue
                }
            }

            // Olhar so as strings nao basta: "Heavy Righteous Metal" e limpo e
            // e coletanea. So comparando com o album que o MusicBrainz devolve
            // da para saber que a faixa esta gravada com o disco errado — era
            // por isso que este botao pulava justamente esses itens.
            val mbResult = lookup.buscar(
                MetadataUtils.cleanFilenameTitle(item.title),
                MetadataUtils.sanitizeArtist(item.artist),
            )

            val acao = ReparoDeTagsPolicy.decidir(
                title = item.title,
                artist = item.artist,
                album = item.album,
                temCapa = item.albumArtPath != null,
                albumDoMusicBrainz = mbResult?.album,
            )
            if (acao == AcaoDeReparo.NADA || acao == AcaoDeReparo.SEM_FONTE) {
                LocalLogger.debug(
                    "Pulando ${item.title} — $acao", tag = "MetadataRepairer"
                )
                skipped++
                continue
            }

            try {
                enriquecedor.enriquecer(
                    audioPath = targetPath,
                    title = MetadataUtils.cleanFilenameTitle(item.title),
                    artist = MetadataUtils.sanitizeArtist(item.artist).takeIf { it.isNotBlank() },
                    album = item.album?.trim()?.takeUnless { MetadataUtils.isUnknownMetadata(it) },
                    downloadId = item.id,
                )
                repaired++
            } catch (e: Exception) {
                LocalLogger.error("Falha ao reparar ${item.title}", e, "MetadataRepairer")
                failed++
            }
        }
        return RepairSummary(repaired, failed, skipped, semArquivo)
    }
}

/**
 * Quatro desfechos distintos, não três: "sem arquivo" (só SAF ou sumido do
 * disco) não é falha, e misturar os dois produzia "626 falharam" sem causa.
 */
data class RepairSummary(
    val repaired: Int,
    val failed: Int,
    val skipped: Int,
    val semArquivo: Int
)
