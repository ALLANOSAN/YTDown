package com.example.ytdown.services

import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.VideoUrl
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadService @Inject constructor(
    private val chaquoDownloadService: ChaquoDownloadService,
    private val scheduler: DownloadScheduler
) {
    fun fetchVideoInfo(url: String): JSONObject = chaquoDownloadService.fetchVideoInfo(url)

    suspend fun startDownload(
        url: VideoUrl,
        path: FilePath,
        metadata: MediaMetadata,
        options: DownloadOptions
    ) {
        scheduler.schedule(url, path, metadata, options)
    }
}
