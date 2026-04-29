package com.example.ytdown.providers

import android.content.Intent
import com.example.ytdown.services.SharingIntentService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingProvider @Inject constructor(
    private val sharingIntentService: SharingIntentService
) {
    fun extractSharedUrl(intent: Intent): String? {
        return sharingIntentService.handleIntent(intent)
    }

    fun cleanUrl(url: String): String {
        return sharingIntentService.cleanUrl(url)
    }
}
