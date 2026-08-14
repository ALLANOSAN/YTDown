package com.example.ytdown.utils

/**
 * Transforma texto arbitrário em nome de arquivo seguro.
 *
 * O título do vídeo vem cru do YouTube e é reaproveitado como nome de arquivo
 * na exportação. `File(pastaDestino, nome)` NÃO colapsa "..", então um título
 * como "../../../../DCIM/pwned" escapava da pasta de destino e sobrescrevia
 * arquivo arbitrário no armazenamento externo (Android <= 9, onde a exportação
 * usa File API direto em vez do MediaStore).
 *
 * O título continua sendo guardado cru no banco — é dado de exibição. A
 * sanitização acontece só na fronteira com o sistema de arquivos.
 */
object FileNameSanitizer {

    /** Separadores de caminho e caracteres que MediaStore/SAF/FAT rejeitam. */
    private val invalidChars = Regex("[\\\\/:*?\"<>|\\r\\n\\t]")

    /**
     * Devolve um nome sem separador de caminho nenhum, ou string vazia quando a
     * entrada não sobra nada aproveitável (".", "..", "/") — nesse caso o
     * chamador deve cair no seu próprio fallback.
     */
    fun safeFileName(raw: String): String {
        val collapsed = raw
            .replace(invalidChars, "_")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (collapsed.isEmpty()) return ""
        // Só pontos/underscores/espaço não é nome de arquivo — "..", ".", "/"
        if (collapsed.all { it == '.' || it == '_' || it.isWhitespace() }) return ""
        return collapsed
    }
}
