package com.example.ytdown.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Visto no aparelho (29/10/2026): "Jekyll & Hyde" do Petra, baixada sem album
 * encontrado, recebeu a capa do "Beyond Belief" — guardada em setembro sob
 * md5("Petra-"), a chave de QUALQUER faixa do Petra sem album. Do mesmo jeito
 * 18 faixas do Fruto Sagrado dividiam uma capa de forro sob md5("David Carvalho-").
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChaveDaCapaTest {

    private lateinit var cache: ArtworkCacheManager

    @Before
    fun preparar() {
        cache = ArtworkCacheManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `sem album duas faixas do mesmo artista nao dividem capa`() {
        assertNotEquals(
            cache.chaveDaCapa("Petra", "", "Jekyll & Hyde"),
            cache.chaveDaCapa("Petra", "", "Armed and Dangerous"),
        )
    }

    /** "YTDown" e sentinela gravada pelo download, nao nome de disco. */
    @Test
    fun `album sentinela conta como sem album`() {
        assertNotEquals(
            cache.chaveDaCapa("Petra", "YTDown", "Jekyll & Hyde"),
            cache.chaveDaCapa("Petra", "YTDown", "Armed and Dangerous"),
        )
    }

    /** Com album real a capa continua compartilhada — e o disco inteiro. */
    @Test
    fun `com album as faixas do disco dividem a capa`() {
        assertEquals(
            cache.chaveDaCapa("Petra", "This Means War!", "Jekyll & Hyde"),
            cache.chaveDaCapa("Petra", "This Means War!", "Kill the Messenger"),
        )
        assertEquals(cache.getCacheKey("Petra", "This Means War!"), cache.chaveDaCapa("Petra", "This Means War!", "x"))
    }
}
