package com.example.ytdown.utils

object YouTubeUtils {
    // Detecta vídeos (watch?v=) e playlists (?list= ou &list=) no YouTube e youtu.be
    private val videoPattern = Regex(
        "(https?://)?(m\\.|www\\.)?youtube\\.com/watch\\?.*v=[a-zA-Z0-9_-]+.*",
        RegexOption.IGNORE_CASE
    )
    private val playlistPattern = Regex(
        "(https?://)?(m\\.|www\\.)?youtube\\.com/(playlist|watch)\\?.*list=[a-zA-Z0-9_-]+.*",
        RegexOption.IGNORE_CASE
    )
    private val shortLinkPattern = Regex(
        "(https?://)?youtu\\.be/[a-zA-Z0-9_-]+.*",
        RegexOption.IGNORE_CASE
    )

    fun extractUrl(raw: String): String? {
        val trimmed = raw.trim()
        val match = videoPattern.find(trimmed)
            ?: playlistPattern.find(trimmed)
            ?: shortLinkPattern.find(trimmed)
            ?: return null

        var url = match.value.replace(Regex("[),.!?\\s]+$"), "")
        if (!url.startsWith("http", true)) {
            url = "https://$url"
        }
        return url
    }

    fun isYouTubeUrl(text: String): Boolean =
        videoPattern.containsMatchIn(text) ||
        playlistPattern.containsMatchIn(text) ||
        shortLinkPattern.containsMatchIn(text)

    fun isPlaylistUrl(text: String): Boolean = playlistPattern.containsMatchIn(text)
}
