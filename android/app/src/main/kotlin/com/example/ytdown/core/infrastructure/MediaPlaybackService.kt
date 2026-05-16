package com.example.ytdown.core.infrastructure

import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.ytdown.MainActivity
import com.example.ytdown.core.audio.*
import com.example.ytdown.core.domain.DownloadItemEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import java.net.URL
import javax.inject.Inject

/**
 * MediaPlaybackService - Serviço de reprodução baseado em BASS.
 * Segue padrões profissionais de sincronização e Audio Focus.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    @Inject
    lateinit var playerManager: MusicPlayerManager
    
    @Inject
    lateinit var stateManager: PlaybackStateManager
    
    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var audioManager: AudioManager
    
    private var isForeground = false

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val CHANNEL_ID = "bass_playback_channel"
        const val NOTIFICATION_ID = 2001
        
        const val ACTION_PLAY = "com.example.ytdown.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.ytdown.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.ytdown.ACTION_NEXT"
        const val ACTION_PREV = "com.example.ytdown.ACTION_PREV"
        const val ACTION_STOP = "com.example.ytdown.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando MediaPlaybackService")
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        setupMediaSession()
        
        // Observar o estado unificado para atualizar o sistema
        serviceScope.launch {
            stateManager.uiState.collect { state ->
                updatePlaybackState(state)
            }
        }
        
        serviceScope.launch {
            stateManager.uiState.map { it.currentTrack }.distinctUntilChanged().collect { track ->
                track?.let { updateMetadata(it) }
            }
        }
    }

    private fun setupMediaSession() {
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(this, "MediaPlaybackService", mediaButtonReceiver, null)
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        mediaSession.setSessionActivity(pendingIntent)

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { playerManager.resume() }
            override fun onPause() { playerManager.pause() }
            override fun onSkipToNext() { playerManager.next() }
            override fun onSkipToPrevious() { playerManager.previous() }
            override fun onStop() { stopSelf() }
            override fun onSeekTo(pos: Long) { playerManager.seekTo(pos) }
        })

        mediaSession.isActive = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YTDown Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de áudio BASS"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        when (intent?.action) {
            ACTION_PLAY -> playerManager.resume()
            ACTION_PAUSE -> playerManager.pause()
            ACTION_NEXT -> playerManager.next()
            ACTION_PREV -> playerManager.previous()
            ACTION_STOP -> stopSelf()
            "UPDATE_METADATA" -> {
                stateManager.uiState.value.currentTrack?.let { updateMetadata(it) }
            }
        }
        
        return START_STICKY
    }

    private fun updatePlaybackState(state: PlaybackUiState) {
        val playbackState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        val builder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(playbackState, state.positionMs, 1.0f)
            
        mediaSession.setPlaybackState(builder.build())
        showNotification()
    }

    private fun updateMetadata(track: DownloadItemEntity) {
        serviceScope.launch(Dispatchers.IO) {
            val builder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "Unknown")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, stateManager.uiState.value.durationMs)

            val imageUrl = track.albumImageUrl ?: track.thumbnailPath
            if (!imageUrl.isNullOrBlank()) {
                try {
                    val bitmap = if (imageUrl.startsWith("http")) {
                        BitmapFactory.decodeStream(URL(imageUrl).openConnection().getInputStream())
                    } else {
                        BitmapFactory.decodeFile(imageUrl)
                    }
                    bitmap?.let {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao carregar capa para notificação: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                mediaSession.setMetadata(builder.build())
                showNotification()
            }
        }
    }

    private fun showNotification() {
        val state = stateManager.uiState.value
        val isPlaying = state.isPlaying
        val track = state.currentTrack ?: return

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setLargeIcon(mediaSession.controller.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )

        // Actions
        builder.addAction(
            android.R.drawable.ic_media_previous, "Previous",
            createPendingIntent(ACTION_PREV)
        )
        
        if (isPlaying) {
            builder.addAction(
                android.R.drawable.ic_media_pause, "Pause",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_play, "Play",
                createPendingIntent(ACTION_PLAY)
            )
        }

        builder.addAction(
            android.R.drawable.ic_media_next, "Next",
            createPendingIntent(ACTION_NEXT)
        )

        val notification = builder.build()
        
        if (!isForeground && isPlaying) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }
}
