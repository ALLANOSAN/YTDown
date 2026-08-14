package com.example.ytdown.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * toTitleCase é aplicado ao título de todo arquivo escaneado
 * (FileSystemScannerService) e ao artista adivinhado do título.
 * Ele não pode destruir nomes que já vêm com caixa correta.
 */
class TitleCaseTest {

    @Test
    fun `capitaliza palavras minusculas`() {
        assertEquals("Numb", MetadataUtils.toTitleCase("numb"))
        assertEquals("Get Back To The Bible", MetadataUtils.toTitleCase("get back to the bible"))
    }

    @Test
    fun `preserva nomes em caixa alta`() {
        assertEquals("AC/DC", MetadataUtils.toTitleCase("AC/DC"))
        assertEquals("MGMT", MetadataUtils.toTitleCase("MGMT"))
        assertEquals("R.E.M.", MetadataUtils.toTitleCase("R.E.M."))
        assertEquals("KISS", MetadataUtils.toTitleCase("KISS"))
    }

    @Test
    fun `preserva caixa interna intencional`() {
        assertEquals("iPhone", MetadataUtils.toTitleCase("iPhone"))
        assertEquals("DragonForce", MetadataUtils.toTitleCase("DragonForce"))
        assertEquals("AC/DC Live", MetadataUtils.toTitleCase("AC/DC live"))
    }

    @Test
    fun `nao quebra em entrada vazia`() {
        assertEquals("", MetadataUtils.toTitleCase(""))
        assertEquals("   ", MetadataUtils.toTitleCase("   "))
    }
}
