package com.example.ytdown.core.business

/**
 * Filtro de indisponíveis antes do enfileiramento (par do probe Python
 * check_availability, que sabe ANTES de baixar — estilo Snaptube).
 *
 * Objeto puro, sem I/O: recebe os specs e o mapa url → disponível vindos do
 * probe e decide quem agenda. Segue o padrão das policies de artwork.
 *
 * Regra de segurança: URL sem resultado no mapa (probe inconclusivo ou
 * erro transiente) SEMPRE agenda — nunca se descarta vídeo por falta de
 * informação; o worker decide nesses casos.
 */
object UnavailableSkipPolicy {

    data class Particao(
        val agendar: List<PlaylistItem>,
        val ignorados: List<PlaylistItem>
    )

    fun partir(
        itens: List<PlaylistItem>,
        disponibilidade: Map<String, Boolean>
    ): Particao {
        val (ignorados, agendar) = itens.partition { spec ->
            disponibilidade[spec.url] == false
        }
        return Particao(agendar = agendar, ignorados = ignorados)
    }
}
