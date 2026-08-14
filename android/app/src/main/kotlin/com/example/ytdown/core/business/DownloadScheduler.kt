package com.example.ytdown.core.business

import androidx.paging.PagingData
import androidx.work.*
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.core.infrastructure.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import com.example.ytdown.utils.LocalLogger

/**
 * Agenda downloads para o [DownloadWorker].
 *
 * Estratégia (pós-refatoração): NÃO encadeamos mais um WorkRequest por item.
 * Em vez disso, marcamos cada item como "pending" no banco e enfileiramos UM
 * único worker foreground de longa duração ("yt_download_queue"). Esse worker
 * drena a fila (próximo "pending" por vez) segurando UM wakelock/wifilock
 * para toda a sessão.
 *
 * Por que isso resolve os bugs anteriores:
 *  - Tela off / app em 2º plano: o worker é um foreground service com wakelock,
 *    então continua baixando item após item mesmo com a tela apagada. O design
 *    antigo (um worker expedited POR item, encadeado) estacionava o próximo item
 *    quando o app saía de primeiro plano, deixando a fila "eterna".
 *  - Cancelamento: um worker único apenas encerra o lote; os itens restantes
 *    ficam "pending" e podem ser reagendados (retry). Não há mais cadeia que
 *    se quebra no primeiro cancelamento.
 *  - Cota de expedited: só há UM worker expedited por vez, nunca esgotando
 *    a cota do Android.
 */
class DownloadScheduler @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager,
    private val storage: StorageResolver
) {
    fun stream(): Flow<List<DownloadItemEntity>> = repository.stream()

    fun streamPaged(query: String = "", typeFilter: Int? = null): Flow<PagingData<DownloadItemEntity>> =
        repository.streamPaged(query, typeFilter)

    suspend fun schedule(
        url: VideoUrl, path: FilePath, meta: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null
    ) {
        LocalLogger.debug("🔍 Scheduling: Artist=${meta.artist.value}, Album=${meta.album.value}, Title=${meta.title.value}", tag = "DownloadScheduler")
        val id = UUID.randomUUID().toString()

        repository.persist(buildEntity(id, url.value, path, meta, options, artworkUrl))

        // Apenas garante que o worker de fila esteja rodando.
        enqueueQueueWorker()
    }

    /**
     * Agenda uma playlist para download SERIAL (um item de cada vez).
     *
     * Todos os itens são persistidos como "pending" NA ORDEM em que aparecem
     * (createdAt crescente = ordem da playlist). O worker de fila os consome
     * nessa mesma ordem, então o PRIMEIRO item da playlist é baixado primeiro.
     */
    suspend fun schedulePlaylist(folder: FilePath, items: List<PlaylistItem>) {
        if (items.isEmpty()) return

        // Base de tempo para garantir ordem determinística de inserção mesmo
        // quando vários itens são persistidos no mesmo milissegundo.
        // Ordena pela posição REAL da playlist (playlistIndex). Itens sem
        // índice (não-playlist) mantêm a ordem recebida.
        val ordered = items.sortedBy { it.index ?: Int.MAX_VALUE }

        val baseTime = System.currentTimeMillis()
        ordered.forEachIndexed { index, spec ->
            val id = UUID.randomUUID().toString()
            repository.persist(
                buildEntity(
                    id = id,
                    url = spec.url,
                    path = folder,
                    meta = spec.meta,
                    options = spec.options,
                    artworkUrl = spec.artworkUrl,
                    createdAt = baseTime + index
                )
            )
        }

        enqueueQueueWorker()
    }

    /**
     * Reagenda um download que falhou mantendo o mesmo [DownloadItemEntity.id].
     * Reseta o status para "pending" e garante que o worker de fila esteja ativo.
     */
    suspend fun retry(item: DownloadItemEntity) {
        // O destino (pasta) já está gravado em item.outputPath desde o agendamento.
        repository.persist(item.copy(status = "pending", progress = 0.0))
        enqueueQueueWorker()
    }

    /** Reagenda todos os itens cujo status for "failed". */
    suspend fun retryAll(items: List<DownloadItemEntity>) {
        items.filter { it.status == "failed" }.forEach { retry(it) }
    }

    /**
     * Enfileira o worker de fila. Usa [ExistingWorkPolicy.KEEP] para NÃO
     * iniciar um segundo worker se um já estiver rodando — o worker ativo
     * vai encontrar o novo item "pending" sozinho ao drenar a fila.
     */
    private fun enqueueQueueWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(QUEUE_WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            QUEUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun typeOf(options: DownloadOptions): Int = if (options.type == DownloadType.AUDIO) 0 else 1

    private fun buildEntity(
        id: String,
        url: String,
        path: FilePath,
        meta: MediaMetadata,
        options: DownloadOptions,
        artworkUrl: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): DownloadItemEntity {
        // O título é guardado cru de propósito — é dado de exibição. Quem monta
        // nome de arquivo (YtDlpWrapper, StorageService) sanitiza na hora de usar.
        return DownloadItemEntity(
            id = id,
            url = url,
            title = meta.title.value,
            // Guardamos a PASTA de destino aqui; o worker a usa como registro.
            outputPath = path.value,
            status = "pending",
            progress = 0.0,
            artist = meta.artist.value,
            album = meta.album.value,
            type = typeOf(options),
            format = options.format,
            quality = options.quality,
            createdAt = createdAt,
            artworkUrl = artworkUrl
        )
    }

    companion object {
        const val QUEUE_WORK_NAME = "yt_download_queue"
    }
}

/**
 * Especificação de um item a ser baixado em cadeia (playlist).
 * Desacopla a montagem do agendamento da UI.
 */
data class PlaylistItem(
    val url: String,
    val meta: MediaMetadata,
    val options: DownloadOptions,
    val artworkUrl: String? = null,
    val index: Int? = null
)
