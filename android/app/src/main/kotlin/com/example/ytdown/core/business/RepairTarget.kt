package com.example.ytdown.core.business

/**
 * Onde (e se) dá para reescrever as tags de um item da biblioteca.
 *
 * Existe porque o guard anterior colapsava três situações diferentes em
 * `failed++`, sem log: item exportado para SAF, arquivo que sumiu do disco e
 * falha real do enriquecimento. Com 626 itens o resultado era
 * "0 de 626 · 626 falharam", que não diz nada sobre a causa.
 */
sealed interface RepairTarget {

    /** Caminho de arquivo comum, regravável direto pelo Mutagen. */
    data class Arquivo(val path: String) : RepairTarget

    /** Só existe como `content://`: reescrita direta não se aplica. */
    data object Saf : RepairTarget

    /** Sem caminho utilizável, ou o arquivo não está mais lá. */
    data object SemArquivo : RepairTarget

    companion object {
        /**
         * Prefere `exportedPath` (é o arquivo que o usuário realmente ouve) e cai
         * para `outputPath`. Um dos dois em SAF não descarta o outro: se o
         * exportado é `content://` mas o interno ainda existe, repara pelo interno.
         *
         * @param existe injetado para manter a regra testável sem tocar o disco.
         */
        fun resolver(
            outputPath: String,
            exportedPath: String?,
            existe: (String) -> Boolean
        ): RepairTarget {
            val candidatos = listOf(exportedPath, outputPath)
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }

            if (candidatos.isEmpty()) return SemArquivo

            candidatos.firstOrNull { !it.startsWith("content://") && existe(it) }
                ?.let { return Arquivo(it) }

            // Nenhum caminho comum utilizável: SAF tem precedência na explicação,
            // já que é limitação conhecida e não sintoma de arquivo perdido.
            return if (candidatos.any { it.startsWith("content://") }) Saf else SemArquivo
        }
    }
}
