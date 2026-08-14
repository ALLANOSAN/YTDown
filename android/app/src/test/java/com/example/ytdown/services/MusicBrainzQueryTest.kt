package com.example.ytdown.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A query Lucene do MusicBrainz é montada à mão. Sem artista, a cláusula
 * `AND artist:"..."` não pode entrar, e aspas no título precisam ser escapadas
 * — senão a query quebra e o enrichment inteiro fica sem fonte de verdade.
 */
class MusicBrainzQueryTest {

    @Test
    fun `monta query com artista`() {
        assertEquals(
            "recording:\"Get Back To The Bible\" AND artist:\"Petra\"",
            MusicBrainzService.buildRecordingQuery("Get Back To The Bible", "Petra")
        )
    }

    @Test
    fun `omite clausula de artista quando vazio`() {
        assertEquals(
            "recording:\"Get Back To The Bible\"",
            MusicBrainzService.buildRecordingQuery("Get Back To The Bible", "")
        )
        assertEquals(
            "recording:\"Get Back To The Bible\"",
            MusicBrainzService.buildRecordingQuery("Get Back To The Bible", "   ")
        )
    }

    @Test
    fun `escapa aspas e barras invertidas`() {
        assertEquals(
            """recording:"Song \"X\"" AND artist:"AC\\DC"""",
            MusicBrainzService.buildRecordingQuery("Song \"X\"", "AC\\DC")
        )
    }
}
