package com.example.ytdown.core.domain

import android.net.Uri
import java.io.File

@JvmInline
value class StoragePath(val value: String) {
    fun toFile(): File = File(value)
}

@JvmInline
value class StorageMediaType(val value: String) {
    fun isAudio(): Boolean = value == "audio"
    fun isVideo(): Boolean = value == "video"
}

@JvmInline
value class StorageMimeType(val value: String)

data class ExportTarget(
    val collection: Uri,
    val relativePath: String,
    val strategy: String,
)

const val SAF_EXPORT_REQUEST_CODE = 40072
