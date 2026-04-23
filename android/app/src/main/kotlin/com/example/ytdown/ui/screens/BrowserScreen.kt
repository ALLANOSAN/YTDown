package com.example.ytdown.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ytdown.core.domain.VideoUrl

@Composable
fun BrowserScreen(onUrlRequest: (VideoUrl) -> Unit) {
    var currentUrl by rememberSaveable { mutableStateOf("https://www.youtube.com") }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            currentUrl = url ?: ""
                        }
                    }
                    settings.javaScriptEnabled = true
                    loadUrl(currentUrl)
                }
            },
            update = { webView -> webView.loadUrl(currentUrl) }
        )

        if (currentUrl.contains("watch") || currentUrl.contains("playlist")) {
            FloatingActionButton(
                onClick = { onUrlRequest(VideoUrl(currentUrl)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Baixar")
            }
        }
    }
}
