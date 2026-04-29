package com.example.ytdown.services

import android.content.Context
import com.example.ytdown.core.infrastructure.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundTaskService @Inject constructor(
    private val notificationHelper: NotificationHelper
) {
    private var activeDownloads = 0

    fun init(context: Context) {
        // O WorkManager gerencia o serviço de foreground do download.
        // Mantemos essa classe para compatibilidade com a arquitetura Flutter original.
        activeDownloads = 0
    }

    fun updateCount(context: Context, count: Int) {
        activeDownloads = count
        if (count <= 0) {
            return
        }
        // Opção de notificar manualmente se desejado
        val progressNotification = notificationHelper.buildProgressNotification("YTDown", 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(999999, progressNotification)
    }

    fun stop(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(999999)
    }
}
