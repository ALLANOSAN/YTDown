package com.example.ytdown.core.business

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Regra 8: Apenas uma variável de instância (dao).
 */
class DownloadRepository @Inject constructor(private val dao: DownloadDao) {

    fun streamAll(): Flow<List<DownloadItemEntity>> = dao.getAllDownloads()

    suspend fun find(id: String): DownloadItemEntity? = dao.getById(id)

    suspend fun persist(item: DownloadItemEntity) = dao.upsert(item)

    suspend fun remove(item: DownloadItemEntity) = dao.delete(item)
}