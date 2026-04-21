package com.example.ytdown.core.business

import android.content.Context
import com.example.ytdown.core.domain.*
import com.example.ytdown.DownloadMetadataManager
import java.io.File

class DownloadEngine(
    private val ytDlp: YtDlpWrapper,
    private val metadataManager: DownloadMetadataManager
) {
    fun downloadAndTag(
        context: Context, 
        url: VideoUrl, 
        output: File, 
        metadata: MediaMetadata,
        options: DownloadOptions
    ): ExitCode {
        val result = ytDlp.downloadVideo(url, output, options)
        
        if (result.isSuccess()) {
            metadataManager.rewriteMetadata(context, FilePath(output.absolutePath), metadata)
        }
        return result
    }
}
