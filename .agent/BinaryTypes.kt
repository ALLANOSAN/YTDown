package com.example.ytdown.core.domain

import java.io.File

@JvmInline
value class VideoInfoJson(val value: String)

@JvmInline
value class AssetPath(val value: String)

@JvmInline
value class VideoUrl(val value: String)

@JvmInline
value class FilePath(val value: String)

@JvmInline
value class MimeType(val value: String)

@JvmInline
value class MediaTitle(val value: String)

@JvmInline
value class ArtistName(val value: String)

@JvmInline
value class AlbumName(val value: String)

data class BinaryConfig(val name: String, val asset: AssetPath)

@JvmInline
value class ExitCode(val value: Int) {
    fun isSuccess(): Boolean = value == 0
}

data class ProcessOutput(val stdOut: String, val stdErr: String)