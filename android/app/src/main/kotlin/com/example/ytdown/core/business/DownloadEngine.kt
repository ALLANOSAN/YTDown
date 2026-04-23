package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.*
import java.io.File

class DownloadEngine(
    private val ytDlp: YtDlpWrapper,
    private val metadataManager: DownloadMetadataManager
) {
    fun downloadAndTag(
        url: VideoUrl,
        output: File,
        metadata: MediaMetadata,
        options: DownloadOptions,
        onProgress: ((Int) -> Unit)? = null
    ): ExitCode {
        val result = ytDlp.downloadVideo(url, output, options, onProgress)
        
        if (result.isSuccess()) {
            metadataManager.rewriteMetadata(FilePath(output.absolutePath), metadata)
        }
        return result
    }
}
