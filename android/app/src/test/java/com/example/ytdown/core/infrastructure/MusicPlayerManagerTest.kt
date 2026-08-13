package com.example.ytdown.core.infrastructure

import android.content.Context
import android.content.SharedPreferences
import com.example.ytdown.core.audio.BassPlaybackEngine
import com.example.ytdown.core.audio.PlaybackController
import com.example.ytdown.core.audio.PlaybackUiState
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerManagerTest {

    private lateinit var manager: MusicPlayerManager
    private lateinit var mockPlayer: BassPlaybackEngine
    private lateinit var mockController: PlaybackController
    private lateinit var mockDao: DownloadDao
    private lateinit var mockMetadataService: MetadataService
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val testDispatcher = StandardTestDispatcher()
    private val uiStateFlow = MutableStateFlow(PlaybackUiState())
    private val playlistContextFlow =
        MutableStateFlow(PlaybackController.PlaylistContext(emptyList(), -1))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayer = mock(BassPlaybackEngine::class.java)
        mockController = mock(PlaybackController::class.java)
        mockDao = mock(DownloadDao::class.java)
        mockMetadataService = mock(MetadataService::class.java)
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("player_state", Context.MODE_PRIVATE)).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockPrefs.getInt("repeat_mode", 0)).thenReturn(0)
        `when`(mockPrefs.getBoolean("shuffle_enabled", false)).thenReturn(false)
        `when`(mockController.uiState).thenReturn(uiStateFlow)
        `when`(mockController.playlistContext).thenReturn(playlistContextFlow)

        manager = MusicPlayerManager(
            mockPlayer,
            mockController,
            mockDao,
            mockMetadataService,
            mockContext
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test repeat mode is saved when uiState changes`() = runTest {
        // Change repeat mode via flow
        uiStateFlow.value = PlaybackUiState(repeatMode = 1)
        
        testDispatcher.scheduler.advanceUntilIdle()

        // It should save repeat mode to SharedPreferences automatically
        verify(mockEditor).putInt("repeat_mode", 1)
        // StateFlow reemite o valor atual ao coletar, entao o collect roda para o
        // estado inicial e de novo para a mudanca: apply() acontece 2x. O que
        // importa e o putInt acima, que so casa com a emissao de repeatMode=1.
        verify(mockEditor, atLeastOnce()).apply()
    }

    @Test
    fun `test shuffle mode is saved when uiState changes`() = runTest {
        // Change shuffle via flow
        uiStateFlow.value = PlaybackUiState(isShuffleEnabled = true)
        
        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockEditor).putBoolean("shuffle_enabled", true)
        verify(mockEditor, atLeastOnce()).apply()
    }

    @Test
    fun `contexto da playlist e persistido quando o controller muda de playlist`() = runTest {
        // A UI toca por PlaybackViewModel -> PlaybackController. Se o contexto nao
        // for persistido a partir dai, playlist_ids nunca e escrito e
        // restorePlaybackState() retorna na primeira linha, sempre.
        playlistContextFlow.value = PlaybackController.PlaylistContext(
            trackIds = listOf("id-a", "id-b", "id-c"),
            index = 1,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockEditor).putString("playlist_ids", "id-a,id-b,id-c")
        verify(mockEditor).putInt("playlist_index", 1)
    }

    @Test
    fun `playlist vazia nao apaga o contexto salvo`() = runTest {
        playlistContextFlow.value = PlaybackController.PlaylistContext(listOf("id-a"), 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Estado inicial do controller emite lista vazia; sobrescrever com ""
        // apagaria a sessao anterior antes de o usuario tocar qualquer coisa.
        playlistContextFlow.value = PlaybackController.PlaylistContext(emptyList(), -1)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockEditor, never()).putString("playlist_ids", "")
    }

    @Test
    fun `test destroy calls saveCurrentPositionNow`() = runTest {
        manager.destroy()
        
        // At least we verify commit is called or putLong is called
        // Even if track is null, the test is to ensure the save code path runs.
        // Actually, let's set a track and position.
        val mockTrack = mock(com.example.ytdown.core.domain.DownloadItemEntity::class.java)
        `when`(mockTrack.id).thenReturn("123")
        uiStateFlow.value = PlaybackUiState(currentTrack = mockTrack, positionMs = 1500L)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        manager.destroy()
        
        verify(mockEditor).putString("last_track_id", "123")
        verify(mockEditor).putLong("last_position_ms", 1500L)
        verify(mockEditor).commit()
    }
}
