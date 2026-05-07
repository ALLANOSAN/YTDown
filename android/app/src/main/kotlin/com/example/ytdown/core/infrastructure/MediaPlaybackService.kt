package com.example.ytdown.core.infrastructure

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ytdown.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    @Inject lateinit var playerManager: MusicPlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerManager.getPlayer()

        // O PendingIntent permite que o usuário volte para o app ao clicar na notificação
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // IMPORTANTE: Ao construir a MediaSession e vinculá-la ao player, 
        // o Media3 já entende que deve exibir os controles de mídia.
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
            
        // NOTA: Não chamamos setMediaNotificationProvider aqui. 
        // Deixamos o Media3 usar o padrão interno para evitar conflitos de ciclo de vida.
    }

    // Este método é obrigatório para que o sistema encontre a sessão
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}