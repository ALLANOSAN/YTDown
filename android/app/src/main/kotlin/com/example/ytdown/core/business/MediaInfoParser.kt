package com.example.ytdown.core.business

import com.example.ytdown.core.domain.VideoInfoJson
import com.example.ytdown.core.domain.MediaTitle
import com.example.ytdown.core.domain.VideoUrl
import org.json.JSONObject

class MediaInfoParser {
    
    // Regra 1: Um nível de indentação
    // Regra 2: Sem ELSE
    fun parseEntries(json: VideoInfoJson): List<Pair<MediaTitle, VideoUrl>> {
        val root = JSONObject(json.value)
        val entries = root.optJSONArray("entries")
        
        if (entries == null) {
            return listOf(extractSingle(root))
        }
        
        return (0 until entries.length()).map { i ->
            extractSingle(entries.getJSONObject(i))
        }
    }

    private fun extractSingle(obj: JSONObject): Pair<MediaTitle, VideoUrl> {
        val title = obj.optString("title", "Unknown")
        val url = obj.optString("webpage_url", obj.optString("url", ""))
        return MediaTitle(title) to VideoUrl(url)
    }
}