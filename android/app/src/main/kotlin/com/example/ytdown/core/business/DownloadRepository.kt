package com.example.ytdown.core.business

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.StorageResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    private val storage: StorageResolver
) {

    fun stream(): Flow<List<DownloadItemEntity>> = dao.getAllDownloads()

    /**
     * Paging3 — retorna PagingData paginado com 30 itens por página.
     * Aceita filtros opcionais de busca e tipo (0=áudio, 1=vídeo, null=todos).
     */
    fun streamPaged(
        query: String = "",
        typeFilter: Int? = null
    ): Flow<PagingData<DownloadItemEntity>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 10,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { dao.getDownloadsPaged() }
    ).flow.map { pagingData ->
        pagingData.filter { item ->
            val typeMatch = typeFilter == null || item.type == typeFilter
            val searchMatch = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.artist?.contains(query, ignoreCase = true) == true
            typeMatch && searchMatch
        }
    }

    suspend fun find(id: String): DownloadItemEntity? = dao.getById(id)

    /** Próximo item "pending"/"queued" na ordem de inserção. */
    suspend fun nextPending(): DownloadItemEntity? = dao.getNextPending()

    /** Libera itens travados em "downloading" de um worker anterior abortado. */
    suspend fun resetStuckDownloading() = dao.resetStuckDownloading()

    suspend fun persist(item: DownloadItemEntity) = dao.upsert(item)

    suspend fun delete(id: String) {
        val item = dao.getById(id) ?: return
        dao.delete(item)
        // outputPath guarda a PASTA de destino (registro), nunca um arquivo real
        // (os arquivos ficam no cache ou no MediaStore). Deletamos só se for um
        // arquivo de fato, para não apagar a pasta de destino do usuário.
        if (item.outputPath.isNotBlank()) {
            try {
                val privateFile = File(item.outputPath)
                if (privateFile.exists() && privateFile.isFile) privateFile.delete()
            } catch (e: Exception) {}
        }
        item.exportedPath?.let { uriString ->
            storage.deleteFromPublicCollection(uriString)
        }
    }
}
