package com.example.ytdown.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O metadata.py escolhe o gravador pela EXTENSAO do arquivo. A copia temporaria
 * de um `content://` tirava a extensao do texto da URI — e URI do MediaStore
 * ("content://media/external/audio/media/42") nao tem ponto, entao todo arquivo
 * virava ".mp3". Um m4a recebia cabecalho ID3 na frente do `ftyp` e era
 * devolvido por cima do original: audio corrompido.
 */
class SafExtensionTest {

    @Test
    fun `m4a do MediaStore e copiado como m4a, nao como mp3`() {
        assertEquals("m4a", MediaImportProcessor.extensaoDoAudio("audio/mp4", "content://media/external/audio/media/42"))
    }

    @Test
    fun `mp3 continua mp3`() {
        assertEquals("mp3", MediaImportProcessor.extensaoDoAudio("audio/mpeg", "content://media/external/audio/media/7"))
    }

    @Test
    fun `sem tipo usa a extensao do nome do documento`() {
        assertEquals("m4a", MediaImportProcessor.extensaoDoAudio(null, "content://x/document/primary%3AMusic%2FFaixa.m4a"))
    }

    /** Formato que o Mutagen do app nao grava: nao arrisca adivinhar. */
    @Test
    fun `formato desconhecido nao vira mp3 por chute`() {
        assertNull(MediaImportProcessor.extensaoDoAudio(null, "content://media/external/audio/media/42"))
        assertNull(MediaImportProcessor.extensaoDoAudio("audio/ogg", "content://media/external/audio/media/42"))
    }
}
