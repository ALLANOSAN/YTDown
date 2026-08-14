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

        if (stripGeneratedSuffix) {
            // Antes de trocar "_" por espaço — senão o sufixo gerado com
            // underscore ("Song_a1b2c3d4"), que é o caso comum, deixa de casar.
            // UUID antes do hex genérico: o genérico comeria o último grupo do
            // UUID e o padrão de UUID nunca mais casaria.
            normalized = uuidSuffixPattern.matcher(normalized).replaceAll("")
            normalized = generatedSuffixPattern.matcher(normalized).replaceAll("")
        }

        // Substitui múltiplos underscores por espaços
        normalized = normalized.replace(Regex("[_]+"), " ")

        // Limpa espaços duplos
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    // Grupo entre parênteses/colchetes, ex: "(m4a 128k)" ou "[320kbps]"
    private val bracketGroupPattern = Regex("""[(\[]([^)\]]*)[)\]]""")

    // Token aceitável dentro de um grupo de lixo: formato, bitrate ou unidade
    private val junkTokenPattern =
        Regex("""^(?:mp3|m4a|aac|ogg|opus|wav|flac|webm|mp4|\d{2,4}(?:k|kb|kbps|kbit)?|k|kb|kbps|kbit|bps)$""", RegexOption.IGNORE_CASE)

    // Pelo menos um token precisa ser "forte" (formato ou bitrate com unidade),
    // senão "(2011)" seria confundido com lixo e o ano do título sumiria.
    private val strongJunkTokenPattern =
        Regex("""^(?:mp3|m4a|aac|ogg|opus|wav|flac|webm|mp4|\d{2,4}(?:k|kb|kbps|kbit)|k|kb|kbps|kbit|bps)$""", RegexOption.IGNORE_CASE)

    // "01. ", "3 - ", "02) " — numeração com pontuação é sempre faixa
    private val punctuatedTrackPattern = Regex("""^\d{1,3}\s*[.\-_)]+\s*""")

    // "02 " — número com zero à esquerda é faixa; "50 Ways" e "99 Problems" não são
    private val paddedTrackPattern = Regex("""^0\d{0,2}\s+""")

    /**
     * Limpa um título derivado de NOME DE ARQUIVO.
     *
     * Remove numeração de faixa e sufixos de formato/bitrate que rippers e sites
     * de conversão deixam no nome, ex:
     *   "02 Get Back To The Bible(m4a 128k)" -> "Get Back To The Bible"
     *
     * Não usar em títulos que já vêm de metadados confiáveis (yt-dlp, MusicBrainz):
     * "02 Ghosts I" e "Song (2011)" são títulos legítimos.
     */
    fun cleanFilenameTitle(value: String): String {
        val normalized = normalizeMetadataText(value)

        val withoutJunk = bracketGroupPattern.replace(normalized) { match ->
            val tokens = match.groupValues[1].split(Regex("""[\s,._-]+""")).filter { it.isNotBlank() }
            val isJunk = tokens.isNotEmpty() &&
                tokens.all { junkTokenPattern.matches(it) } &&
                tokens.any { strongJunkTokenPattern.matches(it) }
            if (isJunk) "" else match.value
        }

        val withoutTrack = withoutJunk
            .replaceFirst(punctuatedTrackPattern, "")
            .replaceFirst(paddedTrackPattern, "")

        val cleaned = withoutTrack.replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { normalized }
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

    /**
     * O item da biblioteca ainda precisa passar pelo enriquecimento?
     *
     * Não basta o campo estar PREENCHIDO. "02 Get Back To The Bible(m4a 128k)"
     * não é sentinela, então o critério antigo (só `isUnknownMetadata`) o dava
     * como pronto e o botão "Reparar Tags" pulava para sempre justamente o caso
     * que ele existe para consertar.
     *
     * O teste do título é ele mesmo contra [cleanFilenameTitle]: se a limpeza
     * muda alguma coisa, o que está gravado ainda é nome de arquivo, não título.
     * Como `cleanFilenameTitle` preserva "50 Ways...", "Song (Live)" e afins,
     * título legítimo não é marcado como sujo — o que importa, senão todo scan
     * reprocessaria a biblioteca inteira e martelaria o MusicBrainz.
     */
    fun needsMetadataRepair(
        title: String?,
        artist: String?,
        album: String?,
        hasArtwork: Boolean
    ): Boolean {
        if (!hasArtwork) return true
        if (isUnknownMetadata(title) || isUnknownMetadata(artist) || isUnknownMetadata(album)) {
            return true
        }
        val atual = title.orEmpty().trim()
        return cleanFilenameTitle(atual) != atual
    }

    /**
     * Converte sentinelas de "sem artista" em string vazia.
     *
     * O parser de filename devolve "Unknown" quando não acha separador. Tratado
     * como artista real, isso vira `artist:"Unknown"` na query do MusicBrainz
     * (zero resultados) e ainda faz o guard de fallback rejeitar o match certo —
     * o arquivo termina sem álbum, sem ano, sem faixa e sem capa.
     */
    fun sanitizeArtist(value: String?): String {
        val trimmed = (value ?: "").trim()
        return if (isUnknownMetadata(trimmed)) "" else trimmed
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
            val firstChar = word.firstOrNull()
            // Só capitaliza palavra que veio inteira em minúsculas. O guard
            // antigo checava a primeira letra de word.lowercase(), que é sempre
            // minúscula — então todo nome passava pelo lowercase() e "AC/DC"
            // virava "Ac/dc", "MGMT" virava "Mgmt", "iPhone" virava "Iphone".
            if (firstChar != null && firstChar.isLowerCase() && word.none { it.isUpperCase() }) {
                word.replaceFirstChar { it.titlecase() }
            } else {
                word
            }
        }
    }
}
