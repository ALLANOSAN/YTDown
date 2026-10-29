package com.example.ytdown.core.infrastructure.persistence

import androidx.paging.PagingSource
import androidx.room.Room
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ordem da lista de downloads (sintoma: "download novo nao fica no topo").
 *
 * Contrato documentado no DownloadViewModel: o sort vem do banco com
 * ORDER BY createdAt DESC (igual a favoritos, histórico e playlists).
 * A fila de execução (getNextPending) é outro contrato e NÃO muda:
 * FIFO por createdAt ASC, senão a ordem da playlist quebra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DownloadDaoOrderingTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadDao

    private fun item(id: String, createdAt: Long, status: String = "completed") =
        DownloadItemEntity(
            id = id,
            url = "https://www.youtube.com/watch?v=$id",
            title = "titulo $id",
            outputPath = "/Music",
            status = status,
            progress = 1.0,
            createdAt = createdAt
        )

    @Before
    fun abreBancoEmMemoria() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.downloadDao()
    }

    @After
    fun fechaBanco() {
        db.close()
    }

    @Test
    fun `lista exibe o download mais novo no topo`() = runTest {
        dao.upsert(item("antigo", createdAt = 1000))
        dao.upsert(item("meio", createdAt = 2000))
        dao.upsert(item("novo", createdAt = 3000))

        val ids = dao.getAllDownloads().first().map { it.id }

        assertEquals(listOf("novo", "meio", "antigo"), ids)
    }

    @Test
    fun `paginacao tambem comeca pelo mais novo`() = runTest {
        dao.upsert(item("antigo", createdAt = 1000))
        dao.upsert(item("meio", createdAt = 2000))
        dao.upsert(item("novo", createdAt = 3000))

        val resultado = dao.getDownloadsPaged().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("novo", "meio", "antigo"), resultado.data.map { it.id })
    }

    @Test
    fun `fila continua FIFO mesmo com lista invertida`() = runTest {
        dao.upsert(item("primeiro-da-fila", createdAt = 1000, status = "pending"))
        dao.upsert(item("segundo-da-fila", createdAt = 2000, status = "pending"))
        dao.upsert(item("ja-pronto", createdAt = 3000, status = "completed"))

        assertEquals("primeiro-da-fila", dao.getNextPending()?.id)
    }
}
