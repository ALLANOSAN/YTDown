package com.example.ytdown.services

import androidx.paging.PagingData
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.VideoUrl
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadFeedService @Inject constructor(
    private val scheduler: DownloadScheduler
) {
    fun stream(): Flow<List<DownloadItemEntity>> = scheduler.stream()

    fun streamPaged(query: String = "", typeFilter: Int? = null): Flow<PagingData<DownloadItemEntity>> =
        scheduler.streamPaged(query, typeFilter)

    suspend fun enqueueDownload(
        url: VideoUrl,
        path: FilePath,
        metadata: MediaMetadata,
        options: DownloadOptions
    ) {
        scheduler.schedule(url, path, metadata, options)
    }
}
