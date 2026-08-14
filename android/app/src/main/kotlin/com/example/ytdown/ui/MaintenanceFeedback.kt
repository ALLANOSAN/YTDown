package com.example.ytdown.ui

/**
 * Texto que a tela de Configurações mostra depois de uma tarefa de manutenção.
 *
 * A mensagem antiga era "Reparo concluído: 0 corrigidos, 0 pulados, 0 falhas" —
 * não dava para distinguir biblioteca vazia de tudo-já-certo de tudo-falhou,
 * então o botão parecia não fazer nada.
 */
object MaintenanceFeedback {

    /**
     * @param semArquivo itens que o app não alcança: exportados só para SAF
     *   (`content://`) ou cujo arquivo sumiu do disco. Não são falha — contá-los
     *   como tal produzia "626 falharam" e escondia a causa real.
     */
    fun reparo(
        total: Int,
        repaired: Int,
        skipped: Int,
        failed: Int,
        semArquivo: Int = 0
    ): String {
        if (total == 0) return "Nenhuma música na biblioteca para reparar"

        if (repaired == 0 && failed == 0 && semArquivo == 0) {
            return "Nada a fazer: as $skipped músicas já têm tags e capa"
        }
        if (repaired == 0 && failed == 0 && semArquivo == total) {
            return "Nenhum arquivo acessível: $semArquivo itens estão só no SAF " +
                "ou sumiram do disco"
        }

        val partes = mutableListOf("$repaired de $total reparadas")
        if (skipped > 0) partes += "$skipped já estavam completas"
        if (failed > 0) partes += "$failed falharam"
        if (semArquivo > 0) partes += "$semArquivo sem arquivo"
        return partes.joinToString(" · ")
    }

    fun erro(tarefa: String, e: Throwable): String {
        val detalhe = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        return "$tarefa falhou: $detalhe"
    }
}
