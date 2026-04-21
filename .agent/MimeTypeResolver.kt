package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MimeType

class MimeTypeResolver {
    private val extensions = mapOf(
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "mp4" to "video/mp4",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "flac" to "audio/flac"
    )

    // Regra 2: Sem ELSE (Uso de map.getOrDefault)
    fun fromPath(path: FilePath): MimeType {
        val extension = path.value.substringAfterLast('.', "").lowercase()
        val mime = extensions.getOrDefault(extension, "application/octet-stream")
        return MimeType(mime)
    }
}