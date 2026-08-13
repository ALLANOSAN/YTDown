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
        verify(mockEditor).apply()
    }

    @Test
    fun `test shuffle mode is saved when uiState changes`() = runTest {
        // Change shuffle via flow
        uiStateFlow.value = PlaybackUiState(isShuffleEnabled = true)
        
        testDispatcher.scheduler.advanceUntilIdle()

        verify(mockEditor).putBoolean("shuffle_enabled", true)
        verify(mockEditor).apply()
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
