package com.example.ytdown.utils

object MetadataUtils {
    private val artistSeparators = listOf(
        " - ",
        " \u2013 ",
        " \u2014 ",
        " | "
    )

    private val generatedSuffixPattern = Regex("[_-][0-9a-f]{6,}$", RegexOption.IGNORE_CASE)
    private val uuidSuffixPattern = Regex("[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

    private val appUnknownValues = setOf("ytdown")
    private val baseUnknownValues = setOf(
        "unknown",
        "unknown artist",
        "desconhecido",
        "artista desconhecido",
        "videoplayback"
    )

    fun isUnknownMetadata(value: String?, additionalUnknownValues: Set<String> = emptySet()): Boolean {
        val normalized = normalizeLowercase(value)
        if (normalized.isEmpty()) return true
        return normalized in baseUnknownValues || normalized in additionalUnknownValues
    }

    fun isUnknownAppMetadata(value: String?): Boolean = isUnknownMetadata(value, additionalUnknownValues = appUnknownValues)

    fun normalizeMetadataText(value: String, stripGeneratedSuffix: Boolean = true): String {
        var normalized = value.trim().replace(Regex("[_]+"), " ")
        if (stripGeneratedSuffix) {
            normalized = normalized.replace(generatedSuffixPattern, "")
        }
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }

    fun guessArtistFromTitle(title: String, additionalUnknownValues: Set<String> = emptySet()): String? {
        val normalizedTitle = normalizeMetadataText(title)
        for (separator in artistSeparators) {
            if (!normalizedTitle.contains(separator)) continue

            val candidate = normalizedTitle.split(separator)[0].trim()
            if (!isUnknownMetadata(candidate, additionalUnknownValues) && candidate.length >= 2) {
                return candidate
            }
        }
        return null
    }

    fun guessAppArtistFromTitle(title: String): String? = guessArtistFromTitle(title, additionalUnknownValues = appUnknownValues)

    fun guessTitleFromPath(path: String, additionalUnknownValues: Set<String> = emptySet()): String? {
        val sanitizedPath = path.trim()
        if (sanitizedPath.isEmpty()) return null

        val filename = sanitizedPath.replace('\\', '/').split('/').lastOrNull().orEmpty()
        if (filename.isEmpty()) return null

        val dotIndex = filename.lastIndexOf('.')
        var baseName = if (dotIndex > 0) filename.substring(0, dotIndex) else filename
        baseName = baseName.replace(uuidSuffixPattern, "")
        baseName = baseName.replace(generatedSuffixPattern, "")

        val normalizedTitle = normalizeMetadataText(baseName)
        if (normalizedTitle.isEmpty()) return null
        if (isUnknownMetadata(normalizedTitle, additionalUnknownValues)) return null
        return normalizedTitle
    }

    fun guessAppTitleFromPath(path: String): String? = guessTitleFromPath(path, additionalUnknownValues = appUnknownValues)

    private fun normalizeLowercase(value: String?): String = value?.trim()?.lowercase() ?: ""
}
