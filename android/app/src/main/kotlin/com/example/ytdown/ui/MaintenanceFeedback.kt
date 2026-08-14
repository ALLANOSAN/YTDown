package com.example.ytdown.ui

/**
 * Texto que a tela de Configurações mostra depois de uma tarefa de manutenção.
 *
 * A mensagem antiga era "Reparo concluído: 0 corrigidos, 0 pulados, 0 falhas" —
 * não dava para distinguir biblioteca vazia de tudo-já-certo de tudo-falhou,
 * então o botão parecia não fazer nada.
 */
object MaintenanceFeedback {

    fun reparo(total: Int, repaired: Int, skipped: Int, failed: Int): String {
        if (total == 0) return "Nenhuma música na biblioteca para reparar"
        if (repaired == 0 && failed == 0) {
            return "Nada a fazer: as $skipped músicas já têm tags e capa"
        }

        val partes = mutableListOf("$repaired de $total reparadas")
        if (skipped > 0) partes += "$skipped já estavam completas"
        if (failed > 0) partes += "$failed falharam"
        return partes.joinToString(" · ")
    }

    fun erro(tarefa: String, e: Throwable): String {
        val detalhe = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        return "$tarefa falhou: $detalhe"
    }
}
