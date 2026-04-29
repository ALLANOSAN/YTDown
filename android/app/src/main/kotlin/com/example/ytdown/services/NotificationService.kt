package com.example.ytdown.services

import android.content.Context
import com.example.ytdown.core.infrastructure.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val context: Context,
    private val notificationHelper: NotificationHelper
) {
    fun showDownloadStarted(id: String, title: String) {
        val notification = notificationHelper.buildProgressNotification(title, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(id.hashCode(), notification)
    }

    fun showDownloadProgress(id: String, title: String, progress: Int) {
        val notification = notificationHelper.buildProgressNotification(title, progress)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(id.hashCode(), notification)
    }

    fun showDownloadCompleted(id: String, title: String) {
        val notification = notificationHelper.buildProgressNotification(title, 100)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(id.hashCode(), notification)
    }

    fun showDownloadFailed(id: String, title: String, error: String) {
        val notification = notificationHelper.buildProgressNotification("$title - falha", 100)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(id.hashCode(), notification)
    }

    fun cancelNotification(id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(id.hashCode())
    }
}
