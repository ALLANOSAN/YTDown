package com.example.ytdown.core.infrastructure

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MimeType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaScanner(private val context: Context) {

    // Regra 1: Um nível de indentação
    // Regra 2: Sem ELSE
    fun scanSync(path: FilePath, mimeType: MimeType): Uri? {
        val latch = CountDownLatch(1)
        var resultUri: Uri? = null

        MediaScannerConnection.scanFile(context, arrayOf(path.value), arrayOf(mimeType.value)) { _, uri ->
            resultUri = uri
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        return resultUri
    }
}