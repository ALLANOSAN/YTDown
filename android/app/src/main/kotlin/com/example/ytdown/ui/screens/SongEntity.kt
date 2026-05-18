package com.example.ytdown.core.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val path: String,
    
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumArtwork: String?,
    val artistArtwork: String?,
    val addedAt: Long = System.currentTimeMillis()
)