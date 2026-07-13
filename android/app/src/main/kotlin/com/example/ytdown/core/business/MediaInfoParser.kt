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
        
        // Mantém (posição no array) junto de cada item para desempatar a
        // ordenção quando o playlist_index estiver ausente (vídeo único).
        val indexed = (0 until entries.length()).map { i ->
            val entryObj = entries.getJSONObject(i)
            val item = extractPreview(entryObj)
            val candidate = if (item.thumbnail == null) {
                item.copy(thumbnail = playlistThumbnail)
            } else item
            i to candidate
        }

        // Ordena pela posição REAL da playlist (playlist_index do yt-dlp).
        // Isso garante "episódio 1 → 2 → 3…" independente de em que
        // ordem o array `entries` vier. Itens sem índice (não-playlist)
        // mantêm a ordem original (fallback pela posição no array).
        return indexed
            .sortedBy { (pos, item) -> item.playlistIndex ?: pos }
            .map { it.second }
    }

    private fun extractPreview(obj: JSONObject): VideoPreviewItem {
        val id = obj.optString("id", System.currentTimeMillis().toString())
        // yt-dlp expõe a posição da playlist como `playlist_index`
        // (às vezes `playlist_autonumber`). Usamos para ordenar o download.
        val playlistIndex =
            (obj.opt("playlist_index") as? Number)?.toInt()?.takeIf { it > 0 }
                ?: (obj.opt("playlist_autonumber") as? Number)?.toInt()?.takeIf { it > 0 }
        return VideoPreviewItem(
            id = id,
            title = MediaTitle(
                obj.optString("title").ifBlank { "Sem título" }
                    .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(120)
            ),
            url = VideoUrl(obj.optString("webpage_url", obj.optString("url", ""))),
            thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() },
            duration = obj.optLong("duration", 0),
            playlistIndex = playlistIndex
        )
    }

    fun guessArtistFromTitle(title: String): String? {
        return MetadataUtils.guessArtistFromTitle(title)
    }

    fun guessAlbumFromTitle(title: String): String? {
        return MetadataUtils.guessAlbumFromTitle(title)
    }
}
