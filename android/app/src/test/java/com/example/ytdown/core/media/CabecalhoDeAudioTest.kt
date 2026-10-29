package com.example.ytdown.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Layout medido no aparelho (29/10/2026, MediaStore 97972): tag ID3v2.3 de
 * 6045 bytes colada na frente de um M4A — `49 44 33 03 00 ...` e, logo depois
 * da tag, a caixa `00 00 00 1c 'ftyp' 'M4A '`. O ffmpeg recusa o arquivo
 * ("moov atom not found"); cortando ate o `ftyp` o audio volta inteiro.
 */
class CabecalhoDeAudioTest {

    private fun syncsafe(n: Int) = byteArrayOf(
        (n shr 21 and 0x7f).toByte(), (n shr 14 and 0x7f).toByte(),
        (n shr 7 and 0x7f).toByte(), (n and 0x7f).toByte(),
    )

    private val ftyp = byteArrayOf(0, 0, 0, 0x1c) + "ftypM4A ".toByteArray() + ByteArray(20)

    private fun id3(tamanho: Int) =
        "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe(tamanho) + ByteArray(tamanho)

    @Test
    fun `m4a com ID3 grudado na frente devolve onde o mp4 comeca`() {
        val estragado = id3(6045) + ftyp

        assertEquals(6055, CabecalhoDeAudio.inicioDoMp4(estragado))
    }

    @Test
    fun `m4a sadio e mp3 sadio nao tem o que cortar`() {
        assertNull(CabecalhoDeAudio.inicioDoMp4(ftyp))
        assertNull(CabecalhoDeAudio.inicioDoMp4(id3(100) + byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0)))
    }

    @Test
    fun `formato vem do conteudo e nao do nome`() {
        assertEquals("m4a", CabecalhoDeAudio.extensao(ftyp))
        assertEquals("mp3", CabecalhoDeAudio.extensao(id3(100) + byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0)))
        assertEquals("mp3", CabecalhoDeAudio.extensao(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0)))
        assertNull(CabecalhoDeAudio.extensao("fLaC".toByteArray() + ByteArray(30)))
    }

    /** O estragado e m4a — e nao mp3 so porque comeca com "ID3". */
    @Test
    fun `m4a estragado e reconhecido como m4a`() {
        assertEquals("m4a", CabecalhoDeAudio.extensao(id3(6045) + ftyp))
    }
}
