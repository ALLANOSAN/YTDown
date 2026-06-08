package com.example.ytdown.core.metadata.model

data class MusicBrainzRecording(
    val title: String,
    val artist: String,
    val album: String,
    val releaseId: String?,
    val artistId: String?,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null
)