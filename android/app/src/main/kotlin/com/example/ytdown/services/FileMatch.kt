package com.example.ytdown.services

object FileMatch {

    /**
     * Escolhe o arquivo que corresponde a um item da biblioteca.
     *
     * O resultado vira `exportedPath`, alvo de toda escrita posterior — reparo
     * de tags, renomear artista, correcao de capa. Casar por prefixo ligava
     * "Behold" ao arquivo de "Behold the Man" e mandava as gravacoes seguintes
     * para a faixa errada.
     */
    fun escolher(nomes: List<String>, title: String, extension: String): String? {
        val alvo = "${title.trim()}.${extension.trim()}"
        return nomes.find { it.trim().equals(alvo, ignoreCase = true) }
    }
}
