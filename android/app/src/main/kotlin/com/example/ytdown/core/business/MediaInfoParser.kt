package com.example.ytdown.core.business

import com.example.ytdown.core.domain.VideoInfoJson
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.core.domain.VideoPreviewItem
import com.example.ytdown.utils.MetadataUtils
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
            thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() },
            duration = obj.optLong("duration", 0)
        )
    }

    fun guessArtistFromTitle(title: MediaTitle): String {
        return MetadataUtils.guessArtistFromTitle(title.value) ?: ""
    }

    fun guessAlbumFromTitle(title: MediaTitle): String {
        return MetadataUtils.guessAppTitleFromPath(title.value) ?: "YTDown"
    }
}