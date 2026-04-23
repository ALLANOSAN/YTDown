package com.example.ytdown.core.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val filePath: String,
    val status: String, // "pending", "downloading", "completed", "failed"
    val progress: Double,
    val artist: String = "",
    val album: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val artistImageUrl: String? = null,
    val albumImageUrl: String? = null,
    val format: String = "mp3"
)