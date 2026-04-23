package com.example.ytdown.utils

object VideoInfoHandler {
    fun parsePayload(info: Map<String, Any?>): VideoInfoPayload {
        val isPlaylist = info["is_playlist"] as? Boolean ?: false
        val entries = parseEntries(info["entries"])
        val thumbnail = info["thumbnail"] as? String
        val effectiveThumbnail = resolveThumbnail(thumbnail, isPlaylist, entries)

        val title = info["title"] as? String ?: "Sem título"
        val artist = info["artist"] as? String
        val album = info["album"] as? String
        val durationSeconds = (info["duration"] as? Number)?.toInt() ?: 0
        val totalDuration = resolveTotalDuration(entries)

        return VideoInfoPayload(
            isPlaylist = isPlaylist,
            title = title,
            artist = artist,
            album = album,
            thumbnail = thumbnail,
            effectiveThumbnail = effectiveThumbnail,
            entries = entries,
            durationSeconds = durationSeconds,
            totalDuration = totalDuration
        )
    }

    private fun parseEntries(value: Any?): List<Map<String, Any?>>? {
        val list = value as? List<*> ?: return null
        return list.mapNotNull { item ->
            val rawMap = item as? Map<*, *> ?: return@mapNotNull null
            val typedMap = mutableMapOf<String, Any?>()
            for ((key, mapValue) in rawMap) {
                val stringKey = key as? String ?: return@mapNotNull null
                typedMap[stringKey] = mapValue
            }
            typedMap
        }
    }

    private fun resolveThumbnail(
        thumbnail: String?,
        isPlaylist: Boolean,
        entries: List<Map<String, Any?>>?
    ): String? {
        if (!thumbnail.isNullOrBlank()) {
            return thumbnail
        }
        if (!isPlaylist || entries.isNullOrEmpty()) {
            return null
        }
        return entries.firstOrNull()?.get("thumbnail") as? String
    }

    private fun resolveTotalDuration(entries: List<Map<String, Any?>>?): Int {
        return entries?.sumOf { (it["duration"] as? Number)?.toInt() ?: 0 } ?: 0
    }
}

data class VideoInfoPayload(
    val isPlaylist: Boolean,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumbnail: String?,
    val effectiveThumbnail: String?,
    val entries: List<Map<String, Any?>>?,
    val durationSeconds: Int,
    val totalDuration: Int
)
