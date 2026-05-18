package com.example.ytdown.core.infrastructure

import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.ytdown.MainActivity
import com.example.ytdown.core.audio.PlaybackActionDispatcher
import com.example.ytdown.core.audio.PlaybackController
import com.example.ytdown.core.audio.PlaybackUiState
import com.example.ytdown.core.domain.DownloadItemEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.net.URL
import javax.inject.Inject

/**
 * MediaPlaybackService - Serviço de reprodução baseado em BASS.
 * SINGLE SOURCE OF TRUTH para MediaSession e Notification.
 *
 * Implementa:
 * - MediaSession (Lockscreen, Media Chip, Bluetooth AVRCP)
 * - MediaNotification com MediaStyle
 * - Audio Focus (perdas temporárias e permanentes)
 * - Controles Bluetooth (pause 1 clique, next 2 cliques)
 * - Foreground Service para Android 14+
 *
 * Referências:
 * - https://developer.android.com/media/media3
 * - https://developer.android.com/develop/background-work/services/fgs/service-types#media-playback
 * - https://developer.android.com/media/implement/surfaces/mobile
 * - https://developer.android.com/media/optimize/audio-focus
 * - https://www.un4seen.com/doc/#bass/bass.html
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val CHANNEL_ID = "ytdown_playback_channel"
        const val NOTIFICATION_ID = 2001

        // Actions para intents
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
    private lateinit var mediaSessionManager: MediaSessionManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isForeground = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentBitmap: Bitmap? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MediaPlaybackService.onCreate()")

        createNotificationChannel()
        setupMediaSession()
        setupAudioFocus()
        setupXiaomiProtection()

        // SINCRONIZAÇÃO CENTRALIZADA

        // SINCRONIZAÇÃO CENTRALIZADA
        // O PlaybackController é a única fonte de verdade
        // Sempre que mudar, atualizamos MediaSession e Notification

        // Observar estado de reprodução
        serviceScope.launch {
            playbackController.uiState.collect { state ->
                Log.d(TAG, "State: isPlaying=${state.isPlaying}, track=${state.currentTrack?.title}")
                updatePlaybackState(state)
                updateNotification(state)
            }
        }

        // Observar mudanças de track
        serviceScope.launch {
            playbackController.uiState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    track?.let { updateMetadata(it) }
                }
        }
    }

    /**
     * Configura MediaSession com todos os callbacks para:
     * - Lockscreen controls
     * - Media chip (Android 13+)
     * - Bluetooth headset (AVRCP: 1=play/pause, 2=next, 3=prev)
     */
    private fun setupMediaSession() {
        // MediaButtonReceiver para botões de hardware
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)

        // Criar MediaSession
        mediaSession = MediaSessionCompat(this, "YTDownMediaSession", mediaButtonReceiver, null)

        // Configurar intent para abrir app ao clicar na notificação
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession.setSessionActivity(pendingIntent)

        // ========== MEDIA SESSION CALLBACKS ==========
        // TODOS os controles externos passam por aqui:
        // - Lockscreen
        // - Media Chip (Android 13+)
        // - Bluetooth headset (AVRCP)
        // - Android Auto
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {

            override fun onPlay() {
                Log.d(TAG, "MediaSession: onPlay()")
                actionDispatcher.play()
            }

            override fun onPause() {
                Log.d(TAG, "MediaSession: onPause()")
                actionDispatcher.pause()
            }

            override fun onStop() {
                Log.d(TAG, "MediaSession: onStop()")
                actionDispatcher.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            override fun onSkipToNext() {
                Log.d(TAG, "MediaSession: onSkipToNext()")
                actionDispatcher.next()
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "MediaSession: onSkipToPrevious()")
                actionDispatcher.previous()
            }

            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "MediaSession: onSeekTo($pos)")
                actionDispatcher.seekTo(pos)
            }

            override fun onFastForward() {
                // Pulsar advance 10 segundos (se suportado pelo player)
                val current = playbackController.uiState.value.positionMs
                actionDispatcher.seekTo(current + 10000)
            }

            override fun onRewind() {
                // Pulsar rewind 10 segundos (se suportado pelo player)
                val current = playbackController.uiState.value.positionMs
                actionDispatcher.seekTo(maxOf(0, current - 10000))
            }

            override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                Log.d(TAG, "MediaSession: onCustomAction($action)")
                when (action) {
                    "TOGGLE_SHUFFLE" -> actionDispatcher.toggleShuffle()
                    "TOGGLE_REPEAT" -> actionDispatcher.toggleRepeatMode()
                }
            }
        })

        // Configurar ações disponíveis para o MediaSession
        // Isso é CRUCIAL para os controles aparecerem no lockscreen e Bluetooth
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        mediaSession.isActive = true
        Log.d(TAG, "MediaSession activated with full media controls")
    }

    /**
     * Configura Audio Focus conforme documentação oficial do Android.
     * https://developer.android.com/media/optimize/audio-focus
     */
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
                .setWillPauseWhenDucked(false) // Não pausar no duck - apenas reduzir volume
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
        }

        // Registrar receiver para eventos de headset
        val intentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(headsetReceiver, intentFilter)
    }

    /**
     * Xiaomi/HyperOS Protection - Configura proteção contra a morte agressiva de serviços.
     * - WakeLock para manter CPU ativa durante reprodução
     * - Auto-restart se o serviço for morto
     */
    private fun setupXiaomiProtection() {
        // Adquirir WakeLock para evitar que CPU durma durante reprodução
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock: PowerManager.WakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "YTDown::MediaPlaybackWakeLock"
            )
            lock.setReferenceCounted(false)
            wakeLock = lock
            Log.d(TAG, "Xiaomi Protection: WakeLock created")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create WakeLock: ${e.message}")
        }

        // Registrar receiver para auto-restart (Xiaomi pode matar o serviço)
        val filter = IntentFilter().apply {
            addAction("android.intent.action.QUICKBOOT_POWERON")
            addAction("com.qualcomm.qti.intent.action.WAKE_LOCK_TIMEOUT")
            // Xiaomi/MIUI specific
            addAction("android.intent.action.BOOT_COMPLETED")
        }
        try {
            registerReceiver(quickBootReceiver, filter)
            Log.d(TAG, "Xiaomi Protection: Auto-restart receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register auto-restart receiver: ${e.message}")
        }
    }

    /**
     * Receiver para tentar restartar o serviço se for morto pelo sistema (Xiaomi).
     */
    private val quickBootReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "QuickBoot action: ${intent?.action}")

            // Se o serviço foi morto mas estava tocando, tentar restartar
            if (playbackController.uiState.value.isPlaying && !isForeground) {
                Log.d(TAG, "Service was killed - attempting auto-restart")
                try {
                    actionDispatcher.play()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-restart failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Adquire o WakeLock para manter reprodução ativa.
     * Chamar quando começar a tocar.
     */
    private fun acquireWakeLock() {
        wakeLock?.let { lock ->
            if (!lock.isHeld) {
                try {
                    lock.acquire(10 * 60 * 1000L) // 10 minutos max
                    Log.d(TAG, "WakeLock acquired")
                } catch (e: Exception) {
                    Log.w(TAG, "WakeLock acquire failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Libera o WakeLock quando para a reprodução.
     */
    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                try {
                    lock.release()
                    Log.d(TAG, "WakeLock released")
                } catch (e: Exception) {
                    Log.w(TAG, "WakeLock release failed: ${e.message}")
                }
            }
        }
    }

    /**
     *处理 Audio Focus Changes conforme documentação.
     *https://developer.android.com/media/optimize/audio-focus
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "AudioFocus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Ganhou foco - recuperar volume e continuar
                Log.d(TAG, "AudioFocus: GAIN - resuming playback")
                actionDispatcher.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perda permanente (outra app começou a tocar)
                // Pausar e abandonar foco
                Log.d(TAG, "AudioFocus: LOSS - pausing")
                actionDispatcher.pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perda temporária (chamada telefônica, etc)
                // Pausar e esperar
                Log.d(TAG, "AudioFocus: LOSS_TRANSIENT - pausing")
                actionDispatcher.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Perda temporária mas pode continuar com volume reduzido
                // Para música, melhor pausar para não ficar abafado
                Log.d(TAG, "AudioFocus: LOSS_TRANSIENT_CAN_DUCK - pausing")
                actionDispatcher.pause()
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager?.abandonAudioFocusRequest(request)
            }
        }
    }

    /**
     * Receiver para eventos de headset Bluetooth/conectado.
     * Pausa quando:
     * - Fone desconectado
     * - Cabo desconectado
     * - Alto-falante externo conectado (AudioBecomingNoisy)
     */
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0) {
                        // Fone desconectado - pausar
                        Log.d(TAG, "Headset disconnected - pausing")
                        actionDispatcher.pause()
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Algo conectado no alto-falante - pausar
                    Log.d(TAG, "Audio becoming noisy - pausing")
                    actionDispatcher.pause()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YTDown Reprodução",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução de áudio"
                setSound(null, null) // Sem som
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // Processar intents de botões de mídia (incluindo Bluetooth)
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        // Processar intents de ação da notificação
        when (intent?.action) {
            ACTION_PLAY -> {
                acquireWakeLock()
                actionDispatcher.play()
            }
            ACTION_PAUSE -> {
                releaseWakeLock()
                actionDispatcher.pause()
            }
            ACTION_PLAY_PAUSE -> {
                if (playbackController.uiState.value.isPlaying) {
                    releaseWakeLock()
                } else {
                    acquireWakeLock()
                }
                actionDispatcher.playPause()
            }
            ACTION_NEXT -> actionDispatcher.next()
            ACTION_PREVIOUS -> actionDispatcher.previous()
            ACTION_STOP -> {
                releaseWakeLock()
                actionDispatcher.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "PLAY_NEW_PLAYLIST" -> {
                Log.d(TAG, "Play new playlist requested")
            }
            "UPDATE_METADATA" -> {
                playbackController.uiState.value.currentTrack?.let { updateMetadata(it) }
            }
        }

        return START_STICKY
    }

    /**
     * Atualiza o estado do MediaSession com base no PlaybackController.
     * Inclui posição atual para lockscreen progress bar.
     */
    private fun updatePlaybackState(state: PlaybackUiState) {
        val playbackState = when {
            state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        // Calcular taxa de reprodução (1.0 = normal)
        val playbackSpeed = if (state.isPlaying) 1.0f else 1.0f

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND
            )
            .setState(playbackState, state.positionMs, playbackSpeed)
            .setActiveQueueItemId(state.currentTrack?.id?.hashCode()?.toLong() ?: 0)

        try {
            mediaSession.setPlaybackState(stateBuilder.build())
            Log.d(TAG, "PlaybackState: $playbackState, position=${state.positionMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback state: ${e.message}")
        }

        // Gerenciar Audio Focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                if (state.isPlaying) {
                    val result = audioManager?.requestAudioFocus(request)
                    Log.d(TAG, "AudioFocus request result: $result")
                }
            }
        }
    }

    /**
     * Atualiza metadados da MediaSession (título, artista, álbum, capa).
     * USA apenas capa do Álbum (não do artista) conforme especificação.
     */
    private fun updateMetadata(track: DownloadItemEntity) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val builder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "Unknown")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playbackController.uiState.value.durationMs)

                // Sempre usar capa do Álbum (nunca do artista) para MediaSession
                // Isso é padrão do Android - a capa do artista pode ser usada no app
                val imageUrl = track.albumImageUrl ?: track.thumbnailPath

                if (!imageUrl.isNullOrBlank()) {
                    val bitmap = loadBitmap(imageUrl)
                    bitmap?.let {
                        // Cache para reuse na notification
                        currentBitmap = it
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
                BitmapFactory.decodeStream(URL(url).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }.getInputStream())
            } else {
                BitmapFactory.decodeFile(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }

    /**
     * Atualiza a notificação com MediaStyle e controles.
     * Usa o bitmap cacheado para performance.
     */
    private fun updateNotification(state: PlaybackUiState) {
        val track = state.currentTrack ?: return
        val isPlaying = state.isPlaying

        // Só mostra notificação quando tocando ou pausado
        if (!isPlaying && !isForeground) return

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Usar bitmap cacheado para performance
        val largeIcon = currentBitmap

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist ?: "Unknown")
            .setLargeIcon(largeIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(createActionPendingIntent(ACTION_STOP))
            )

        // Adicionar controles de mídia
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
            // Iniciar como foreground service (necessário para Android 14+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requer foregroundServiceType
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13 - tipo mediaPlayback configurado no manifest
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            Log.d(TAG, "Started foreground service")
        } else if (isPlaying) {
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Updated notification")
        } else {
            // Quando pausado, manter notificação mas não foreground
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Paused - notification visible")
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

        // Liberar wake lock
        releaseWakeLock()

        // Desregistrar receivers
        try {
            unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            // Receiver pode não estar registrado
        }
        try {
            unregisterReceiver(quickBootReceiver)
        } catch (e: Exception) {
            // Receiver pode não estar registrado
        }

        abandonAudioFocus()
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }
}