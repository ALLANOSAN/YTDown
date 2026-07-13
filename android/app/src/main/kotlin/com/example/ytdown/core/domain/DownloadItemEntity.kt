package com.example.ytdown.core.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entidade de Download com Índices de Performance.
 * Migrado do Flutter (DatabaseService v9).
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["type", "status"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val outputPath: String,
    val status: String, // "queued", "downloading", "completed", "failed"
    val progress: Double,
    val artist: String? = null,
    val album: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val albumArtPath: String? = null,
    val artistArtPath: String? = null,
    val format: String = "mp3",
    val quality: String = "192",
    val type: Int = 0, // 0: Audio, 1: Video
    val exportedPath: String? = null,
    /** URL da capa resolvida no agendamento (usada pelo engine p/ embutir metadata). */
    val artworkUrl: String? = null
) {
    val folderName: String
        get() = outputPath.substringBeforeLast("/", "Downloads").substringAfterLast("/")
}
