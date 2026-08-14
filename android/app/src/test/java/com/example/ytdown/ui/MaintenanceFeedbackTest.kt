package com.example.ytdown.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O botao "Reparar Tags" nao dizia nada util: a mensagem final era
 * "Reparo concluído: 0 corrigidos, 0 pulados, 0 falhas", que nao distingue
 * "biblioteca vazia" de "tudo ja estava certo" de "tudo falhou".
 */
class MaintenanceFeedbackTest {

    @Test
    fun `biblioteca vazia diz que nao ha o que fazer`() {
        val msg = MaintenanceFeedback.reparo(total = 0, repaired = 0, skipped = 0, failed = 0)
        assertEquals("Nenhuma música na biblioteca para reparar", msg)
    }

    @Test
    fun `tudo pulado explica que ja estava completo`() {
        val msg = MaintenanceFeedback.reparo(total = 12, repaired = 0, skipped = 12, failed = 0)
        assertEquals("Nada a fazer: as 12 músicas já têm tags e capa", msg)
    }

    @Test
    fun `reparo parcial mostra o que mudou e o que ja estava pronto`() {
        val msg = MaintenanceFeedback.reparo(total = 10, repaired = 3, skipped = 7, failed = 0)
        assertEquals("3 de 10 reparadas · 7 já estavam completas", msg)
    }

    @Test
    fun `falhas aparecem na mensagem`() {
        val msg = MaintenanceFeedback.reparo(total = 10, repaired = 6, skipped = 2, failed = 2)
        assertTrue("deveria citar as falhas: $msg", msg.contains("2 falharam"))
        assertTrue("deveria citar as reparadas: $msg", msg.contains("6"))
    }

    @Test
    fun `so falhas nao finge sucesso`() {
        val msg = MaintenanceFeedback.reparo(total = 4, repaired = 0, skipped = 0, failed = 4)
        assertTrue("deveria citar as falhas: $msg", msg.contains("4 falharam"))
        assertFalse("nao pode dizer concluido: $msg", msg.contains("Nada a fazer"))
    }

    /**
     * "626 falharam" nao distinguia falha real de arquivo que o app nao alcanca
     * (exportado so para SAF, ou sumido do disco). Sem separar, nao da para
     * saber se o problema e o pipeline ou o armazenamento.
     */
    @Test
    fun `arquivo inalcancavel nao e contado como falha`() {
        val msg = MaintenanceFeedback.reparo(
            total = 626, repaired = 0, skipped = 0, failed = 0, semArquivo = 626
        )
        assertFalse("nao pode dizer que falhou: $msg", msg.contains("falharam"))
        assertTrue("deveria explicar o motivo: $msg", msg.contains("626"))
        assertTrue("deveria citar arquivo: $msg", msg.contains("arquivo", ignoreCase = true))
    }

    @Test
    fun `mistura de resultados aparece separada`() {
        val msg = MaintenanceFeedback.reparo(
            total = 10, repaired = 4, skipped = 3, failed = 1, semArquivo = 2
        )
        assertTrue(msg, msg.contains("4 de 10"))
        assertTrue(msg, msg.contains("3 já estavam completas"))
        assertTrue(msg, msg.contains("1 falharam"))
        assertTrue(msg, msg.contains("2 sem arquivo"))
    }

    @Test
    fun `erro vira mensagem legivel`() {
        assertEquals(
            "Reparo falhou: disco cheio",
            MaintenanceFeedback.erro("Reparo", RuntimeException("disco cheio"))
        )
    }

    @Test
    fun `erro sem mensagem usa o nome da excecao`() {
        val msg = MaintenanceFeedback.erro("Reparo", IllegalStateException())
        assertFalse("nao pode mostrar null: $msg", msg.contains("null"))
        assertTrue("deveria citar o tipo: $msg", msg.contains("IllegalStateException"))
    }
}
