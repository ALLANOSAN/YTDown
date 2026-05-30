package com.example.ytdown.core.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MetadataExtractor @Inject constructor(
    @param:ApplicationContext
    private val context: Context
) {

    fun extract(audioPath: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            if (audioPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(audioPath))
            } else {
                retriever.setDataSource(audioPath)
            }

            val title = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE
            ) ?: "Unknown"

            val artist = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            ) ?: "Unknown"

            val album = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            ) ?: "Unknown"

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            return AudioMetadata(title, artist, album, duration)
        } catch (e: Exception) {
            return AudioMetadata("Unknown", "Unknown", "Unknown", 0L)
        } finally {
            retriever.release()
        }
    }
}