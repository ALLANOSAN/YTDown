package com.example.ytdown.services

import android.content.Intent
import com.example.ytdown.utils.YouTubeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serviço especializado em tratar Intents de compartilhamento.
 * Limpa URLs e extrai o link real do YouTube.
 */
@Singleton
class SharingIntentService @Inject constructor() {

    fun handleIntent(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        
        // 1. Extrai a URL se houver texto ao redor
        val url = YouTubeUtils.extractUrl(sharedText) ?: sharedText
        
        // 2. Limpa parâmetros de tracking (si, feature, etc)
        return cleanUrl(url)
    }

    fun cleanUrl(url: String): String {
        return try {
            val regex = Regex("([?&])(si|feature|app|embed)=[^&]*")
            var cleaned = url.replace(regex, "")
            
            // Se o caractere após o domínio for '?' e removemos tudo, limpa o '?'
            if (cleaned.endsWith("?") || cleaned.endsWith("&")) {
                cleaned = cleaned.dropLast(1)
            }
            cleaned
        } catch (e: Exception) {
            url
        }
    }
}
