package com.example.ytdown.core.infrastructure

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
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
        val player = playerManager.getPlayer()

        createNotificationChannel()

        // ✅ FIX: vincula o canal customizado ao Media3.
        // Sem isso, o DefaultMediaNotificationProvider cria seu próprio canal interno
        // e ignora o VISIBILITY_PUBLIC que definimos — a notificação não aparece
        // na tela de bloqueio nem na barra de status.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                // ✅ setChannelName() omitido: já definido em createNotificationChannel()
                // e o método espera @StringRes Int, não String direta
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
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
