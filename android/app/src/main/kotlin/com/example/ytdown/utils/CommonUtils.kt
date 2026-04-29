package com.example.ytdown.utils

import android.net.Uri
import java.util.Calendar
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object CommonUtils {
    fun normalizeText(raw: String?): String = raw?.trim() ?: ""

    fun hasText(raw: String?): Boolean = normalizeText(raw).isNotEmpty()

    fun normalizeNullableText(raw: String?): String? {
        val normalized = normalizeText(raw)
        if (normalized.isEmpty()) {
            return null
        }
        return normalized
    }

    fun isRemoteHttpUri(uri: Uri?): Boolean {
        return uri != null && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
    }

    /**
     * Formata bytes em MB, GB, etc.
     * Migrado do Flutter (lib/utils/common_utils.dart).
     */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Formata segundos em HH:MM:SS ou MM:SS.
     */
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        }
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}

object AppDateUtils {
    fun toDayKey(date: java.util.Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }
}
