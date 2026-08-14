package com.example.ytdown.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * O volume vindo da sessao de midia (Now Bar, Bluetooth, mute do sistema)
 * chegava so no StateFlow, via PlaybackController.updateVolume — que nao toca
 * no BASS. O engine so relê `uiState.value.volume` ao criar stream, entao
 * mudar o volume com musica tocando nao mudava o audio.
 *
 * BassPlaybackEngine.setVolume() faz as duas coisas (BASS_ATTRIB_VOL + estado)
 * e nao tinha nenhum chamador.
 */
class PlaybackActionDispatcherVolumeTest {

    private val controller = mock(PlaybackController::class.java)
    private val engine = mock(BassPlaybackEngine::class.java)
    private val dispatcher = PlaybackActionDispatcherImpl(controller, engine)

    @Test
    fun `setVolume chega no engine e nao so no estado`() {
        dispatcher.setVolume(0.42f)
        verify(engine).setVolume(0.42f)
    }

    @Test
    fun `mute manda zero para o engine`() {
        dispatcher.setVolume(0f)
        verify(engine).setVolume(0f)
    }

    @Test
    fun `volume fora da faixa e limitado ao intervalo do BASS`() {
        // BASS_ATTRIB_VOL espera 0..1; valor fora disso e comportamento indefinido.
        assertEquals(1f, PlaybackActionDispatcherImpl.coerceVolume(3.5f), 0f)
        assertEquals(0f, PlaybackActionDispatcherImpl.coerceVolume(-2f), 0f)
        assertEquals(0.5f, PlaybackActionDispatcherImpl.coerceVolume(0.5f), 0f)
        assertEquals(0f, PlaybackActionDispatcherImpl.coerceVolume(Float.NaN), 0f)
    }

    @Test
    fun `volume fora da faixa nao chega cru no engine`() {
        dispatcher.setVolume(9f)
        verify(engine).setVolume(1f)
    }
}
