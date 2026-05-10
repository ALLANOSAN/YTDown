package com.example.ytdown.core.infrastructure

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ytdown.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    @Inject lateinit var playerManager: MusicPlayerManager

    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // ✅ CRITICAL FIX: startForeground() DEVE ser chamado ANTES de qualquer processamento
        // para evitar ForegroundServiceDidNotStartInTimeException
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YTDown")
            .setContentText("Reproduzindo música...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        val player = playerManager.getPlayer()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .build()
        )

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {})
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reprodução de Música",
                // ✅ FIX: IMPORTANCE_LOW é o correto para media player.
                // IMPORTANCE_HIGH fazia a notificação emitir som ao aparecer pela
                // primeira vez — comportamento indesejado para um player de música.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução de áudio"
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playerManager.getPlayer()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Salva a posição final antes de destruir
        playerManager.saveCurrentPositionNow()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
