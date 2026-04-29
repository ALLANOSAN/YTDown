package com.example.ytdown.core.business

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.StorageResolver
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    private val storage: StorageResolver
) {

    fun stream(): Flow<List<DownloadItemEntity>> = dao.getAllDownloads()

    suspend fun find(id: String): DownloadItemEntity? = dao.getById(id)

    suspend fun persist(item: DownloadItemEntity) = dao.upsert(item)

    /**
     * Deleção Completa (Migrado do Flutter DownloadService -> deleteDownload).
     * Apaga do Banco + Arquivo Privado + Arquivo no MediaStore (Público).
     */
    suspend fun delete(id: String) {
        val item = dao.getById(id) ?: return
        
        // 1. Apaga do Banco de Dados
        dao.delete(item)

        // 2. Apaga o arquivo privado apenas se existir um caminho local.
        if (item.outputPath.isNotBlank()) {
            try {
                val privateFile = File(item.outputPath)
                if (privateFile.exists()) privateFile.delete()
            } catch (e: Exception) {}
        }

        // 3. Apaga o arquivo no MediaStore (se exportado)
        item.exportedPath?.let { uriString ->
            storage.deleteFromPublicCollection(uriString)
        }
    }
}
