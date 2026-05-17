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
 * SINGLE SOURCE OF TRUTH para MediaSession e Notification.
 * 
 * Responsabilidades:
 * - MediaSession callbacks (lockscreen, media chip, Bluetooth)
 * - Notificação com controles
 * - Sincronização de estado com PlaybackController
 * - Audio Focus
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val CHANNEL_ID = "bass_playback_channel"
        const val NOTIFICATION_ID = 2001
        
        // Actions
        const val ACTION_PLAY = "com.ytdown.action.PLAY"
        const val ACTION_PAUSE = "com.ytdown.action.PAUSE"
        const val ACTION_PLAY_PAUSE = "com.ytdown.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.ytdown.action.NEXT"
        const val ACTION_PREVIOUS = "com.ytdown.action.PREVIOUS"
        const val ACTION_STOP = "com.ytdown.action.STOP"
    }

    @Inject
    lateinit var playbackController: PlaybackController
    
    @Inject
    lateinit var actionDispatcher: PlaybackActionDispatcher

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var isForeground = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MediaPlaybackService.onCreate()")
        
        createNotificationChannel()
        setupMediaSession()
        setupAudioFocus()
        
        // ========== SINCRONIZAÇÃO CENTRALIZADA ==========
        // O estado do PlaybackController é a única fonte de verdade.
        // Sempre que mudar, atualizamos a MediaSession.
        
        // Observar mudanças no estado de reprodução
        serviceScope.launch {
            playbackController.uiState.collect { state ->
                Log.d(TAG, "State changed: isPlaying=${state.isPlaying}, track=${state.currentTrack?.title}")
                updatePlaybackState(state)
                updateNotification(state)
            }
        }
        
        // Observar mudanças de track para atualizar metadados
        serviceScope.launch {
            playbackController.uiState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    track?.let { updateMetadata(it) }
                }
        }
    }

    private fun setupMediaSession() {
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(this, "MediaPlaybackService", mediaButtonReceiver, null)
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession.setSessionActivity(pendingIntent)

        // ========== MEDIA SESSION CALLBACKS ==========
        // Todos os controles externos (lockscreen, Bluetooth, media chip) passam por aqui
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            
            override fun onPlay() {
                Log.d(TAG, "MediaSession Callback: onPlay()")
                actionDispatcher.play()
            }
            
            override fun onPause() {
                Log.d(TAG, "MediaSession Callback: onPause()")
                actionDispatcher.pause()
            }
            
            override fun onStop() {
                Log.d(TAG, "MediaSession Callback: onStop()")
                actionDispatcher.pause()
                stopSelf()
            }
            
            override fun onSkipToNext() {
                Log.d(TAG, "MediaSession Callback: onSkipToNext()")
                actionDispatcher.next()
            }
            
            override fun onSkipToPrevious() {
                Log.d(TAG, "MediaSession Callback: onSkipToPrevious()")
                actionDispatcher.previous()
            }
            
            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "MediaSession Callback: onSeekTo($pos)")
                actionDispatcher.seekTo(pos)
            }
            
            override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                Log.d(TAG, "MediaSession Callback: onCustomAction($action)")
                when (action) {
                    "TOGGLE_SHUFFLE" -> actionDispatcher.toggleShuffle()
                    "TOGGLE_REPEAT" -> actionDispatcher.toggleRepeatMode()
                }
            }
        })

        mediaSession.isActive = true
        Log.d(TAG, "MediaSession activated")
    }

    private fun setupAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
        }
        
        // Registrar receiver para headset events
        val intentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(headsetReceiver, intentFilter)
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "Audio focus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Recuperar volume e continuar reprodução
                actionDispatcher.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perder foco - pausar
                actionDispatcher.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perda temporária - pausar
                actionDispatcher.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Reduzir volume temporariamente
                // O BASS vai controlar isso automaticamente
            }
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0) {
                        // Fone desconectado - pausar
                        actionDispatcher.pause()
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Algo conectado no alto-falante externo - pausar
                    actionDispatcher.pause()
                }
            }
        }
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
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        // Processar intents de botões de mídia
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        // Processar intents de ação da notificação
        when (intent?.action) {
            ACTION_PLAY -> actionDispatcher.play()
            ACTION_PAUSE -> actionDispatcher.pause()
            ACTION_PLAY_PAUSE -> actionDispatcher.playPause()
            ACTION_NEXT -> actionDispatcher.next()
            ACTION_PREVIOUS -> actionDispatcher.previous()
            ACTION_STOP -> {
                actionDispatcher.pause()
                stopSelf()
            }
            "PLAY_NEW_PLAYLIST" -> {
                // O MusicPlayerManager iniciou isso
                Log.d(TAG, "Play new playlist requested")
            }
            "UPDATE_METADATA" -> {
                // Atualizar metadados da notificação
                playbackController.uiState.value.currentTrack?.let { updateMetadata(it) }
            }
        }
        
        return START_STICKY
    }

    /**
     * Atualiza o estado do MediaSession com base no PlaybackController state.
     * Este método é chamado automaticamente quando o estado muda.
     */
    private fun updatePlaybackState(state: PlaybackUiState) {
        val playbackState = when {
            state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        
        val stateBuilder = PlaybackStateCompat.Builder()
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
            .setActiveQueueItemId(state.currentTrack?.id?.hashCode()?.toLong() ?: 0)
        
        try {
            mediaSession.setPlaybackState(stateBuilder.build())
            Log.d(TAG, "PlaybackState updated: $playbackState, position=${state.positionMs}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback state: ${e.message}")
        }
        
        // Gerenciar Audio Focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                if (state.isPlaying) {
                    audioManager?.requestAudioFocus(request)
                } else {
                    audioManager?.abandonAudioFocusRequest(request)
                }
            }
        }
    }

    /**
     * Atualiza os metadados da MediaSession (título, artista, capa, etc).
     */
    private fun updateMetadata(track: DownloadItemEntity) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val builder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "Unknown")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playbackController.uiState.value.durationMs)

                // REQUISITO: Apenas Album Art na MediaSession (Lockscreen/Bluetooth)
                // NUNCA usar artistImageUrl aqui.
                val imageUrl = track.albumImageUrl ?: track.thumbnailPath
                
                if (!imageUrl.isNullOrBlank()) {
                    val bitmap = loadBitmap(imageUrl)
                    bitmap?.let {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    }
                }

                withContext(Dispatchers.Main) {
                    mediaSession.setMetadata(builder.build())
                    Log.d(TAG, "Metadata updated: ${track.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating metadata: ${e.message}")
            }
        }
    }

    private fun loadBitmap(url: String): Bitmap? {
        return try {
            if (url.startsWith("http")) {
                BitmapFactory.decodeStream(URL(url).openConnection().getInputStream())
            } else {
                BitmapFactory.decodeFile(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }

    /**
     * Atualiza a notificação com controles.
     */
    private fun updateNotification(state: PlaybackUiState) {
        val track = state.currentTrack ?: return
        val isPlaying = state.isPlaying
        
        // Se não está tocando e não é foreground, não fazer nada
        if (!isPlaying && !isForeground) return
        
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist ?: "Unknown")
            .setLargeIcon(mediaSession.controller.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // Actions da notificação
        builder.addAction(
            android.R.drawable.ic_media_previous, 
            "Previous",
            createActionPendingIntent(ACTION_PREVIOUS)
        )
        
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            createActionPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        )
        
        builder.addAction(
            android.R.drawable.ic_media_next, 
            "Next",
            createActionPendingIntent(ACTION_NEXT)
        )

        val notification = builder.build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        if (isPlaying && !isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
            Log.d(TAG, "Started foreground")
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Updated notification")
        }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 
            action.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        try {
            unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            // Receiver pode não estar registrado
        }
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }
}