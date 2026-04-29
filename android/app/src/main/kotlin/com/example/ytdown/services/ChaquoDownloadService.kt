package com.example.ytdown.services

import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.domain.DownloadOptions
import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.MediaMetadata
import com.example.ytdown.core.domain.VideoUrl
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChaquoDownloadService @Inject constructor(
    private val ytDlpWrapper: YtDlpWrapper
) {
    fun fetchVideoInfo(url: String): JSONObject {
        return ytDlpWrapper.fetchVideoInfo(url)
    }

    fun downloadVideo(
        url: VideoUrl,
        outputDir: File,
        options: DownloadOptions,
        metadata: MediaMetadata
    ): ExitCode {
        return ytDlpWrapper.downloadVideo(
            url = url,
            outputDir = outputDir,
            options = options,
            metadata = metadata
        )
    }
}
