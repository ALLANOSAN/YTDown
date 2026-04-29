package com.example.ytdown.core.infrastructure

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

/**
 * Utilitário de notificações com sanitização de títulos.
 * Migrado do Flutter (lib/services/notification_service.dart).
 */
class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel"
    private val maxTitleLength = 50
    
    private val sensitivePathPattern = Pattern.compile("/data/user/\\d+/[a-zA-Z0-9\\-\\.]+(/\\S*)?|/storage/emulated/\\d+(/\\S*)?")

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        val channel = NotificationChannel(
            channelId, 
            "Downloads", 
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificações de download do YTDown"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun sanitizeTitle(title: String): String {
        val clean = sensitivePathPattern.matcher(title).replaceAll(".../arquivo")
        var result = clean
        if (clean.length > maxTitleLength) {
            result = "${clean.take(maxTitleLength - 3)}..."
        }
        return result
    }

    fun buildProgressNotification(title: String, progress: Int): android.app.Notification {
        val safeTitle = sanitizeTitle(title)
        var contentTitle = "⬇️ $progress% - $safeTitle"
        if (progress >= 100) {
            contentTitle = "✅ Download concluído!"
        }

        var contentText: String? = null
        if (progress < 100) {
            contentText = safeTitle
        }

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(progress < 100)
            .setOnlyAlertOnce(true)
            .setAutoCancel(progress >= 100)
            .build()
    }
}
