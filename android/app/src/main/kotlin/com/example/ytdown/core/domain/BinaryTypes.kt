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

data class MediaMetadata(
    val title: MediaTitle,
    val artist: ArtistName,
    val album: AlbumName
)

data class VideoPreviewItem(
    val id: String, // Adicionado ID para facilitar seleção
    val title: MediaTitle,
    val url: VideoUrl,
    val thumbnail: String?,
    val duration: Long,
    val isSelected: Boolean = true // Para seleção em playlists
)

data class DownloadOptions(
    val type: DownloadType,
    val format: String, // Ex: "mp3", "mp4"
    val quality: String // Ex: "192", "1080p"
)

enum class DownloadType(val value: String) {
    AUDIO("audio"),
    VIDEO("video")
}

data class BinaryConfig(val name: String, val asset: AssetPath)

@JvmInline
value class ExitCode(val value: Int) {
    fun isSuccess(): Boolean = value == 0
}

data class ProcessOutput(val stdOut: String, val stdErr: String)
