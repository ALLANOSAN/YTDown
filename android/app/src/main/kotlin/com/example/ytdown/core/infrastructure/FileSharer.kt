package com.example.ytdown.core.infrastructure

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.ytdown.core.domain.FilePath
import java.io.File

class FileSharer(private val context: Context) {

    fun shareFile(filePath: FilePath) {
        val file = File(filePath.value)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartilhar música"))
    }
}