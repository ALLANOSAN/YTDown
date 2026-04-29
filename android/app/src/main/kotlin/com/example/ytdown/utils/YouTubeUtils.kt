package com.example.ytdown.utils

object YouTubeUtils {
    private val youtubeUrlPattern = Regex(
        "(https?:\\/\\/)?(www\\.)?(youtube\\.com\\/\\S+|youtu\\.be\\/\\S+)",
        RegexOption.IGNORE_CASE
    )

    /**
     * 🛡️ Sanitização Agressiva: Remove pontuações e lixo de tracking no final do link.
     * Migrado do Flutter (lib/providers/browser_provider.dart).
     */
    fun extractUrl(raw: String): String? {
        val trimmed = raw.trim()
        val match = youtubeUrlPattern.find(trimmed) ?: return null
        
        // Remove lixo final: ) , . ! ? e espaços
        var url = match.value.replace(Regex("[),.!?\\s]+$"), "")
        
        if (!url.startsWith("http", true)) {
            url = "https://$url"
        }
        return url
    }

    fun isYouTubeUrl(text: String): Boolean = youtubeUrlPattern.containsMatchIn(text)
}
