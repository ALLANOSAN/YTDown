package com.example.ytdown

import android.net.Uri
import java.io.File

@JvmInline
value class FilePath(val value: String) {
    fun toFile(): File = File(value)
}

@JvmInline
value class MediaType(val value: String) {
    fun isAudio(): Boolean = value == "audio"
    fun isVideo(): Boolean = value == "video"
}

@JvmInline
value class MimeType(val value: String)

data class ExportTarget(
    val collection: Uri,
    val relativePath: String,
    val strategy: String,
)

const val SAF_EXPORT_REQUEST_CODE = 40072
