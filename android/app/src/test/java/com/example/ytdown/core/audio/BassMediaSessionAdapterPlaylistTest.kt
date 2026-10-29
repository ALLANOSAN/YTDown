package com.example.ytdown.core.audio

import com.example.ytdown.core.artwork.ArtworkRotationController
import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Tocava a faixa 8 de uma playlist longa e tocou no grupo "Desconhecido"
 * (1 música): FATAL "currentMediaItemIndex must be less than
 * playlist.size()" em setPlaylistFromEntities -> invalidateState -> getState.
 *
 * A troca de lista não ajustava o índice: o 7 da playlist antiga viajava
 * para a playlist nova de tamanho 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BassMediaSessionAdapterPlaylistTest {

    private val estado = MutableStateFlow(PlaybackUiState())
    private val controlador = Mockito.mock(PlaybackController::class.java).also {
        Mockito.`when`(it.uiState).thenReturn(estado)
    }
    private val despachante = Mockito.mock(PlaybackActionDispatcherImpl::class.java)

    private fun faixa(i: Int) = DownloadItemEntity(
        id = "faixa-$i",
        url = "",
        title = "Faixa $i",
        outputPath = "/tmp/faixa-$i.mp3",
        status = "completed",
        progress = 1.0,
    )

    private val adaptador by lazy {
        BassMediaSessionAdapter(controlador, despachante, ArtworkRotationController())
    }

    @Test
    fun `trocar para playlist menor recomeca do indice zero sem crash`() {
        adaptador.setPlaylistFromEntities((0 until 10).map(::faixa))

        // A UI elege a faixa 8 (o collector do adapter sincroniza o índice
        // pelo currentTrack, igual ao play de verdade faz).
        estado.value = estado.value.copy(currentTrack = faixa(7))
        ShadowLooper.idleMainLooper()

        // O tap no grupo de 1 música: a lista nova tem tamanho 1.
        adaptador.setPlaylistFromEntities(listOf(faixa(99)))
        ShadowLooper.idleMainLooper()

        assertEquals(0, adaptador.currentMediaItemIndex)
    }

    @Test
    fun `estado continua valido logo apos a troca de playlist`() {
        adaptador.setPlaylistFromEntities((0 until 10).map(::faixa))
        estado.value = estado.value.copy(currentTrack = faixa(9))
        ShadowLooper.idleMainLooper()

        adaptador.setPlaylistFromEntities(listOf(faixa(1), faixa(2)))
        ShadowLooper.idleMainLooper()

        assertEquals(2, adaptador.mediaItemCount)
        assertEquals(0, adaptador.currentMediaItemIndex)
    }

    init {
        RuntimeEnvironment.getApplication()
    }
}
