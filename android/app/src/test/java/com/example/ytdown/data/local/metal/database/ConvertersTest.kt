package com.example.ytdown.data.local.metal.database

import com.example.ytdown.data.local.metal.entities.DownloadStatus
import com.example.ytdown.data.local.metal.entities.InteractionType
import com.example.ytdown.data.local.metal.entities.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mesma classe de defeito que o fuzzing achou no `_parse_mp4_number`:
 * conversão de string externa sem fallback.
 *
 * `Enum.valueOf` lança `IllegalArgumentException` para qualquer string que não
 * seja exatamente o nome de uma constante. Sendo TypeConverter do Room, ele roda
 * a cada leitura da coluna — uma linha com valor desconhecido derruba a query
 * inteira, não só aquela linha. Basta renomear ou remover uma constante numa
 * versão futura para o app não conseguir mais ler o próprio banco.
 *
 * Na mesma classe, `toLongList` já usa `toLongOrNull()` — a convenção certa
 * estava três linhas abaixo.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `valor conhecido sobrevive ao round-trip`() {
        for (status in SyncStatus.entries) {
            assertEquals(status, converters.toSyncStatus(converters.fromSyncStatus(status)))
        }
        for (status in DownloadStatus.entries) {
            assertEquals(status, converters.toDownloadStatus(converters.fromDownloadStatus(status)))
        }
        for (type in InteractionType.entries) {
            assertEquals(type, converters.toInteractionType(converters.fromInteractionType(type)))
        }
    }

    @Test
    fun `SyncStatus desconhecido vira STALE em vez de lancar`() {
        assertEquals(SyncStatus.STALE, converters.toSyncStatus("CONSTANTE_QUE_NAO_EXISTE"))
        assertEquals(SyncStatus.STALE, converters.toSyncStatus(""))
        assertEquals(SyncStatus.STALE, converters.toSyncStatus("synced"))
    }

    @Test
    fun `DownloadStatus desconhecido vira NOT_DOWNLOADED em vez de lancar`() {
        assertEquals(DownloadStatus.NOT_DOWNLOADED, converters.toDownloadStatus("LIXO"))
        assertEquals(DownloadStatus.NOT_DOWNLOADED, converters.toDownloadStatus(""))
    }

    @Test
    fun `InteractionType desconhecido vira UNKNOWN em vez de lancar`() {
        assertEquals(InteractionType.UNKNOWN, converters.toInteractionType("LIXO"))
        assertEquals(InteractionType.UNKNOWN, converters.toInteractionType(""))
    }

    @Test
    fun `listas continuam tolerantes a lixo`() {
        assertEquals(listOf(1L, 3L), converters.toLongList("1,dois,3"))
        assertEquals(emptyList<Long>(), converters.toLongList(""))
        assertEquals(listOf("a", "b"), converters.toStringList("a|||b"))
    }
}
