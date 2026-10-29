package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.media.CabecalhoDeAudio
import com.example.ytdown.utils.LocalLogger
import com.example.ytdown.utils.MetadataUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Le o inicio de um arquivo de audio — caminho comum ou `content://`. */
interface LeitorDeCabecalho {
    suspend fun ler(caminho: String, bytes: Int): ByteArray?
}

class LeitorDeCabecalhoAndroid @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
) : LeitorDeCabecalho {
    override suspend fun ler(caminho: String, bytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val entrada = if (caminho.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(caminho))
            } else {
                File(caminho).takeIf { it.isFile }?.inputStream()
            }
            // readNBytes so existe a partir do Android 13; minSdk e 26.
            entrada?.use { stream ->
                val buffer = ByteArray(bytes)
                var lidos = 0
                while (lidos < bytes) {
                    val n = stream.read(buffer, lidos, bytes - lidos)
                    if (n < 0) break
                    lidos += n
                }
                buffer.copyOf(lidos)
            }
        } catch (e: Exception) {
            LocalLogger.debug("Cabecalho ilegivel: $caminho (${e.message})", tag = "LeitorDeCabecalho")
            null
        }
    }
}

/**
 * Botao "Recuperar arquivos" — conserta o que bugs antigos deixaram na
 * biblioteca e que o "Reparar Tags" nao enxerga, porque titulo, artista e
 * album parecem limpos:
 *  - m4a com tag ID3 colada na frente (o chute ".mp3" do SAF): o
 *    enriquecimento corta o prefixo e regrava as tags;
 *  - capa sob a chave "artista sem album", compartilhada por toda faixa do
 *    artista sem album: e descartada e buscada de novo, por faixa.
 */
@Singleton
class RecuperadorDeBiblioteca @Inject constructor(
    private val biblioteca: BibliotecaDeAudio,
    private val enriquecedor: EnriquecedorDeItem,
    private val leitor: LeitorDeCabecalho,
    private val cache: ArtworkCacheManager,
) {

    suspend fun recuperar(onProgress: (Float, String) -> Unit): RecuperacaoSummary {
        val itens = biblioteca.itens()
        var arquivos = 0
        var capas = 0
        var falhas = 0

        itens.forEachIndexed { i, item ->
            onProgress((i + 1) / itens.size.toFloat(), "Verificando: ${item.title}")

            // content:// primeiro: e o arquivo que o usuario ouve; o outputPath
            // de download exportado e so a pasta de destino.
            val alvo = listOfNotNull(item.exportedPath, item.outputPath)
                .filter { it.isNotBlank() }
                .firstNotNullOfOrNull { caminho ->
                    leitor.ler(caminho, CabecalhoDeAudio.BYTES_PARA_LER)?.let { caminho to it }
                } ?: return@forEachIndexed

            val estragado = CabecalhoDeAudio.inicioDoMp4(alvo.second) != null
            val contaminada = capaContaminada(item)
            if (!estragado && !contaminada) return@forEachIndexed

            try {
                if (contaminada) {
                    // Sem isto o merge do enriquecimento manteria a capa errada
                    // quando nenhuma fonte devolve capa nova.
                    biblioteca.atualizar(item.copy(albumArtPath = null))
                    capas++
                }
                enriquecedor.enriquecer(
                    audioPath = alvo.first,
                    title = MetadataUtils.cleanFilenameTitle(item.title),
                    artist = MetadataUtils.sanitizeArtist(item.artist).takeIf { it.isNotBlank() },
                    album = item.album?.trim()?.takeUnless { MetadataUtils.isUnknownMetadata(it) },
                    downloadId = item.id,
                )
                if (estragado) arquivos++
            } catch (e: Exception) {
                LocalLogger.error("Falha ao recuperar ${item.title}", e, "RecuperadorDeBiblioteca")
                falhas++
            }
        }
        return RecuperacaoSummary(arquivos, capas, falhas)
    }

    /**
     * Capa do cache de albuns (nome md5) num item sem album conhecido, mas que
     * nao e a chave por faixa dele: veio da chave antiga "artista sem album".
     */
    private fun capaContaminada(item: DownloadItemEntity): Boolean {
        val capa = item.albumArtPath?.let { File(it).nameWithoutExtension } ?: return false
        if (!capa.matches(MD5)) return false
        if (!MetadataUtils.isUnknownMetadata(item.album)) return false
        return capa != cache.chaveDaCapa(item.artist.orEmpty(), item.album.orEmpty(), item.title)
    }

    private companion object {
        val MD5 = Regex("[0-9a-f]{32}")
    }
}

data class RecuperacaoSummary(
    val arquivosRecuperados: Int,
    val capasDescartadas: Int,
    val falhas: Int,
)
