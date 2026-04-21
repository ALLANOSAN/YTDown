package com.example.ytdown.core.business

import com.example.ytdown.core.domain.VideoInfoJson
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.core.domain.VideoPreviewItem
import org.json.JSONObject

class MediaInfoParser {
    
    // Regra 1: Um nível de indentação
    // Regra 2: Sem ELSE
    fun parseEntries(json: VideoInfoJson): List<VideoPreviewItem> {
        val data = JSONObject(json.value).optJSONObject("data") ?: return emptyList()
        val entries = data.optJSONArray("entries")
        
        if (entries == null) {
            return listOf(extractPreview(data))
        }
        
        return (0 until entries.length()).map { i ->
            extractPreview(entries.getJSONObject(i))
        }
    }

    private fun extractPreview(obj: JSONObject): VideoPreviewItem {
        return VideoPreviewItem(
            title = MediaTitle(obj.optString("title", "Unknown")),
            url = VideoUrl(obj.optString("webpage_url", obj.optString("url", ""))),
            thumbnail = obj.optString("thumbnail", null),
            duration = obj.optLong("duration", 0)
        )
    }

    fun guessArtistFromTitle(title: MediaTitle): String {
        val parts = title.value.split(" - ", " – ", " — ", " | ", " by ", " / ")
        if (parts.size > 1) {
            val artistCandidate = parts[0].trim()
            if (artistCandidate.isNotEmpty()) {
                return artistCandidate
            }
        }
        return ""
    }

    fun guessAlbumFromTitle(title: MediaTitle): String {
        // Para playlists, o título da playlist pode ser um bom álbum.
        // Para vídeos únicos, um valor padrão ou o artista pode ser usado.
        return "YTDown" // Fallback padrão
    }
}