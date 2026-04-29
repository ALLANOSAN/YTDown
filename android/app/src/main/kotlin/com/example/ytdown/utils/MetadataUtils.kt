package com.example.ytdown.utils

import java.util.regex.Pattern

/**
 * Utilitários para processamento de metadados de mídia.
 * Migrado do Flutter (lib/utils/metadata_utils.dart) para garantir paridade de lógica.
 */
object MetadataUtils {
    
    private val artistSeparators = listOf(" - ", " – ", " — ", " | ")
    
    private val generatedSuffixPattern = Pattern.compile("[_-][0-9a-f]{6,}$", Pattern.CASE_INSENSITIVE)
    private val uuidSuffixPattern = Pattern.compile("[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE)
    
    private val baseUnknownValues = setOf(
        "unknown", "unknown artist", "desconhecido", "artista desconhecido",
        "videoplayback", "ytdown", "n/a", "sem título"
    )

    fun normalizeMetadataText(value: String, stripGeneratedSuffix: Boolean = true): String {
        var normalized = value.trim()
        
        // Substitui múltiplos underscores por espaços
        normalized = normalized.replace(Regex("[_]+"), " ")

        if (stripGeneratedSuffix) {
            normalized = generatedSuffixPattern.matcher(normalized).replaceAll("")
            normalized = uuidSuffixPattern.matcher(normalized).replaceAll("")
        }

        // Limpa espaços duplos
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Tenta adivinhar o artista a partir do título do vídeo.
     * Ex: "Linkin Park - Numb" -> "Linkin Park"
     */
    fun guessArtistFromTitle(title: String): String? {
        val normalizedTitle = normalizeMetadataText(title)
        
        for (separator in artistSeparators) {
            if (normalizedTitle.contains(separator)) {
                val candidate = normalizedTitle.split(separator).first().trim()
                if (!isUnknownMetadata(candidate) && candidate.length >= 2) {
                    return toTitleCase(candidate)
                }
            }
        }
        return null
    }

    fun guessAlbumFromTitle(title: String): String? {
        val normalizedTitle = normalizeMetadataText(title)

        for (separator in artistSeparators) {
            if (normalizedTitle.contains(separator)) {
                val parts = normalizedTitle.split(separator, limit = 2)
                if (parts.size == 2) {
                    val candidate = parts[1].trim()
                    if (!isUnknownMetadata(candidate) && candidate.length >= 2) {
                        return toTitleCase(candidate)
                    }
                }
            }
        }
        return null
    }

    fun isUnknownMetadata(value: String?): Boolean {
        val normalized = (value ?: "").trim().lowercase()
        return normalized.isEmpty() || baseUnknownValues.contains(normalized)
    }

    /**
     * Converte texto para Title Case (Ex: "numb" -> "Numb")
     */
    fun toTitleCase(input: String): String {
        if (input.isBlank()) return input
        return input.split(" ").joinToString(" ") { word ->
            var resultWord = word
            val firstChar = word.lowercase().firstOrNull()
            if (firstChar != null && firstChar.isLowerCase()) {
                resultWord = word.lowercase().replaceFirstChar { it.titlecase() }
            }
            resultWord
        }
    }
}
