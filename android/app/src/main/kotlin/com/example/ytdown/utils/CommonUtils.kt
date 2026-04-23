package com.example.ytdown.utils

import android.net.Uri
import java.util.Calendar

object YouTubeUtils {
    private val youtubeUrlPattern = Regex(
        "(https?:\\/\\/)?(www\\.)?(youtube\\.com\\/\\S+|youtu\\.be\\/\\S+)",
        RegexOption.IGNORE_CASE
    )

    fun extractUrl(raw: String): String? {
        val trimmed = raw.trim()
        val match = youtubeUrlPattern.find(trimmed) ?: return null
        var url = match.value.replace(Regex("[),.!?]+$"), "")
        if (!url.startsWith("http", true)) {
            url = "https://$url"
        }
        return url
    }

    fun isYouTubeUrl(text: String): Boolean = youtubeUrlPattern.containsMatchIn(text)
}

object CommonUtils {
    fun normalizeText(raw: String?): String = raw?.trim() ?: ""

    fun hasText(raw: String?): Boolean = normalizeText(raw).isNotEmpty()

    fun normalizeNullableText(raw: String?): String? {
        val normalized = normalizeText(raw)
        return if (normalized.isEmpty()) null else normalized
    }

    fun isRemoteHttpUri(uri: Uri?): Boolean {
        return uri != null && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
    }
}

object AppDateUtils {
    fun toDayKey(date: java.util.Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", year, month, day)
    }
}

object DownloadErrorUtils {
    private const val MaxFriendlyErrorLength = 100

    private val generatedSuffixPattern = Regex("[_-][0-9a-f]{6,}$", RegexOption.IGNORE_CASE)

    fun simplify(error: String): String {
        val normalizedError = error.trim()

        return when {
            normalizedError.contains("Sign in to confirm", ignoreCase = true) ->
                "YouTube bloqueou a requisição (Bot check)"
            normalizedError.contains("Video unavailable", ignoreCase = true) ->
                "Vídeo indisponível"
            normalizedError.contains("confirm your age", ignoreCase = true) ->
                "Vídeo com restrição de idade"
            normalizedError.length <= MaxFriendlyErrorLength -> normalizedError
            else -> normalizedError.substring(0, MaxFriendlyErrorLength) + "..."
        }
    }

    fun normalizeReason(errorMessage: String): String {
        val lower = errorMessage.lowercase()
        return when {
            lower.contains("bot check") || lower.contains("sign in to confirm") -> "youtube_bot_check"
            lower.contains("indispon") || lower.contains("unavailable") -> "video_unavailable"
            lower.contains("idade") || lower.contains("age") -> "age_restricted"
            lower.contains("network") || lower.contains("timeout") -> "network_error"
            else -> "unknown"
        }
    }
}
