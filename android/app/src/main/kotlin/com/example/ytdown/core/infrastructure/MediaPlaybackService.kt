package com.example.ytdown.core.infrastructure

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.ytdown.MainActivity
import com.example.ytdown.core.audio.BassMediaSessionAdapter
import com.example.ytdown.core.audio.PlaybackActionDispatcher
import com.example.ytdown.core.audio.PlaybackController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * MediaPlaybackService - Serviço de reprodução baseado em BASS + Media3.
 *
 * MIGRADO para Media3 MediaSessionService:
 * - Suporte a Android 16 Live Island (Now Bar)
 * - MediaSession do Media3 (não mais MediaSessionCompat)
 * - Notificação gerenciada automaticamente pelo Media3
 * - Audio Focus gerenciado automaticamente pelo Media3
 * - Media Buttons (Bluetooth, headset) via Media3
 *
 * Mantém:
 * - Xiaomi/HyperOS protection (WakeLock, auto-restart)
 * - Headset disconnect handling
 *
 * Referências:
 * - https://developer.android.com/reference/kotlin/androidx/media3/session/MediaSessionService
 * - https://developer.android.com/reference/kotlin/androidx/media3/session/MediaSession
 * - https://developer.android.com/media/media3/session/background-playback
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val CHANNEL_ID = "ytdown_playback_channel"
        const val NOTIFICATION_ID = 2001
    }

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var actionDispatcher: PlaybackActionDispatcher

    @Inject
    lateinit var bassAdapter: BassMediaSessionAdapter

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    // ========== MediaSessionService Lifecycle ==========

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MediaPlaybackService.onCreate() - Media3")

        createNotificationChannel()

        // Configurar o provider de notificação do Media3
        // DefaultMediaNotificationProvider usa "default_media_notification_channel_id" por padrão
        // Precisamos criar esse canal também
        createDefaultMediaChannel()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

        setupMediaSession()
        setupXiaomiProtection()
        setupHeadsetReceiver()

        // Observar mudanças de track para log
        serviceScope.launch {
            playbackController.uiState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    track?.let {
                        Log.d(TAG, "Track changed: ${it.title}")
                    }
                }
        }
    }

    /**
     * Called by MediaSessionService when a controller connects.
     * Returns the MediaSession for the controller.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // ========== MediaSession Setup ==========

    private fun setupMediaSession() {
        // Create MediaSession with the BASS adapter as Player
        mediaSession = MediaSession.Builder(this, bassAdapter)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        Log.d(TAG, "Media3 MediaSession created with BASS adapter (auto-active)")
    }

    // ========== Xiaomi/HyperOS Protection ==========

    private fun setupXiaomiProtection() {
        // WakeLock para manter CPU ativa durante reprodução
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "YTDown::MediaPlaybackWakeLock"
            )
            lock.setReferenceCounted(false)
            wakeLock = lock
            Log.d(TAG, "Xiaomi Protection: WakeLock created")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create WakeLock: ${e.message}")
        }

        // Auto-restart receiver para Xiaomi
        val filter = IntentFilter().apply {
            addAction("android.intent.action.QUICKBOOT_POWERON")
            addAction("com.qualcomm.qti.intent.action.WAKE_LOCK_TIMEOUT")
            addAction("android.intent.action.BOOT_COMPLETED")
        }
        try {
            registerReceiver(quickBootReceiver, filter)
            Log.d(TAG, "Xiaomi Protection: Auto-restart receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register auto-restart receiver: ${e.message}")
        }
    }

    private val quickBootReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "QuickBoot action: ${intent?.action}")
            if (playbackController.uiState.value.isPlaying && mediaSession == null) {
                Log.d(TAG, "Service was killed - attempting auto-restart")
                try {
                    actionDispatcher.play()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-restart failed: ${e.message}")
                }
            }
        }
    }

    // ========== Headset Handling ==========

    private fun setupHeadsetReceiver() {
        val intentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            registerReceiver(headsetReceiver, intentFilter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register headset receiver: ${e.message}")
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0) {
                        Log.d(TAG, "Headset disconnected - pausing")
                        actionDispatcher.pause()
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d(TAG, "Audio becoming noisy - pausing")
                    actionDispatcher.pause()
                }
            }
        }
    }

    // ========== Notification Channel ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YTDown Reprodução",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução de áudio"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Cria o canal que o DefaultMediaNotificationProvider usa por padrão.
     * O channelId padrão é "default_media_notification_channel_id".
     */
    private fun createDefaultMediaChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                "YTDown Mídia",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de mídia"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ========== WakeLock Management ==========

    private fun acquireWakeLock() {
        wakeLock?.let { lock ->
            if (!lock.isHeld) {
                try {
                    lock.acquire(10 * 60 * 1000L)
                    Log.d(TAG, "WakeLock acquired")
                } catch (e: Exception) {
                    Log.w(TAG, "WakeLock acquire failed: ${e.message}")
                }
            }
        }
    }

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

    // ========== Service Lifecycle ==========

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // CRÍTICO: Chamar startForeground() IMEDIATAMENTE para evitar
        // ForegroundServiceDidNotStartInTimeException no Android 12+
        // Usar notificação com MediaSession token para controles na tela de bloqueio
        try {
            val sessionToken = mediaSession?.sessionCompatToken
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YTDown")
                .setContentText("Preparando reprodução...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)

            // Adicionar MediaSession token para controles na tela de bloqueio
            if (sessionToken != null) {
                notificationBuilder.setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
            }

            startForeground(NOTIFICATION_ID, notificationBuilder.build())
            Log.d(TAG, "startForeground() com MediaSession token executado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar startForeground: ${e.message}")
        }

        // Handle wake lock based on playback state
        if (playbackController.uiState.value.isPlaying) {
            acquireWakeLock()
        }

        // Deixar o Media3 processar o intent (media buttons, etc.)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        releaseWakeLock()

        try {
            unregisterReceiver(headsetReceiver)
        } catch (e: Exception) { /* may not be registered */ }

        try {
            unregisterReceiver(quickBootReceiver)
        } catch (e: Exception) { /* may not be registered */ }

        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null

        super.onDestroy()
    }

    // ========== Task Removed (swipe away) ==========

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // No media playing - stop service
            stopSelf()
        }
    }
}
