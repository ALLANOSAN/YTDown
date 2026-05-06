package com.example.ytdown.services

import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class ChaquoDownloadService
@Inject
constructor(private val ytDlpWrapper: YtDlpWrapper, private val orchestrator: BinaryOrchestrator) {
    fun fetchVideoInfo(url: String): JSONObject {
        return ytDlpWrapper.fetchVideoInfo(url, orchestrator.getAppFilesDir())
    }

    fun downloadVideo(
            url: VideoUrl,
            outputDir: File,
            options: DownloadOptions,
            metadata: MediaMetadata
    ): Any {
        return ytDlpWrapper.downloadVideo(
                url = url,
                outputDir = outputDir,
                options = options,
                metadata = metadata
        )
    }
}
