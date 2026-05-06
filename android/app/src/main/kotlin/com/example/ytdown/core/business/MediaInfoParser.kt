package com.example.ytdown.core.business

import com.example.ytdown.core.domain.VideoInfoJson
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.core.domain.VideoPreviewItem
import com.example.ytdown.utils.MetadataUtils
import org.json.JSONObject

/**
 * Parser de Informações de Vídeo com Fallback de Thumbnail.
 * Migrado do Flutter (lib/utils/video_info_handler.dart).
 */
class MediaInfoParser {
    
    fun parseEntries(json: VideoInfoJson): List<VideoPreviewItem> {
        val data = JSONObject(json.value).optJSONObject("data") ?: return emptyList()
        val entries = data.optJSONArray("entries")
        
        if (entries == null) {
            return listOf(extractPreview(data))
        }
        
        // Lógica de Fallback: Se a playlist não tiver thumbnail, tenta pegar do primeiro item
        var playlistThumbnail = data.optString("thumbnail").takeIf { it.isNotBlank() }
        if (playlistThumbnail == null && entries.length() > 0) {
            playlistThumbnail = entries.getJSONObject(0).optString("thumbnail").takeIf { it.isNotBlank() }
        }
        
        return (0 until entries.length()).map { i ->
            val entryObj = entries.getJSONObject(i)
            val item = extractPreview(entryObj)
            
            var itemCandidate = item
            if (item.thumbnail == null) {
                itemCandidate = item.copy(thumbnail = playlistThumbnail)
            }
            itemCandidate
        }
    }

    private fun extractPreview(obj: JSONObject): VideoPreviewItem {
        val id = obj.optString("id", System.currentTimeMillis().toString())
        return VideoPreviewItem(
            id = id,
            title = MediaTitle(obj.optString("title", "Unknown")),
            url = VideoUrl(obj.optString("webpage_url", obj.optString("url", ""))),
            thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() },
            duration = obj.optLong("duration", 0)
        )
    }

    fun guessArtistFromTitle(title: String): String? {
        return MetadataUtils.guessArtistFromTitle(title)
    }

    fun guessAlbumFromTitle(title: String): String? {
        return MetadataUtils.guessAlbumFromTitle(title)
    }
}
