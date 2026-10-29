package com.example.ytdown.core.business

/**
 * Nome exibido e navegável dos grupos da Biblioteca (abas Artistas/Álbuns).
 *
 * O banco carrega o literal "null" em artista/album: `optString` sobre
 * `JSONObject.NULL` devolve a string "null", não nulo. Sem este mapeamento o
 * grupo aparece escrito "null" na tela e o clique quebra o NavController —
 * nenhum escape salva o segmento "null" ("Wrong argument type for
 * 'playlistId' in argument savedState").
 *
 * A regra é centralizada aqui para que a lista (groupBy) e o detalhe
 * (filter) concordem: a chave que agrupa é a mesma que filtra.
 */
object AgrupamentoDaBiblioteca {

    const val DESCONHECIDO = "Desconhecido"

    /**
     * Nulo, vazio ou o literal "null" viram "Desconhecido".
     * Nome legítimo passa intacto — inclusive com "/" ou acento.
     */
    fun chave(valor: String?): String =
        if (valor.isNullOrBlank() || valor.equals("null", ignoreCase = true)) {
            DESCONHECIDO
        } else {
            valor
        }

    /**
     * O item pertence ao grupo se a chave dele é o título do grupo.
     * Comparar chave com chave (e não valor cru com título) é o que faz
     * a faixa de artista "null" aparecer dentro de "Desconhecido".
     */
    fun pertenceAoGrupo(valor: String?, tituloDoGrupo: String): Boolean =
        chave(valor) == tituloDoGrupo
}
