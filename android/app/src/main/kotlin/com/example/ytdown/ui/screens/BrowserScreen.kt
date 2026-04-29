package com.example.ytdown.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.providers.browserProvider
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

/**
 * Navegador interno com blindagem de erros (Google Auth).
 * Migrado do Flutter (lib/screens/browser_screen.dart).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onUrlRequest: (VideoUrl) -> Unit) {
    val browserState by browserProvider.state.collectAsState()
    var urlText by rememberSaveable { mutableStateOf(browserState.currentUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(browserState.currentUrl) {
        urlText = browserState.currentUrl
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Navegador", color = Color.White) },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (browserState.isYoutube) {
                var targetButtonScale = 1f
                if (browserState.isLoading) {
                    targetButtonScale = 0.92f
                }
                val buttonScale by animateFloatAsState(targetButtonScale)
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!browserState.isLoading) onUrlRequest(VideoUrl(browserState.currentUrl))
                    },
                    modifier = Modifier.scale(buttonScale),
                    containerColor = YTDownPurple,
                    contentColor = Color.White
                ) {
                    if (browserState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Carregando", fontWeight = FontWeight.SemiBold)
                }
                if (!browserState.isLoading) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Baixar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Baixar", fontWeight = FontWeight.SemiBold)
                }
                }
            }
        },
        containerColor = SurfaceDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Pesquisar ou digitar URL", color = TextSecondary) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = YTDownPurple,
                            focusedContainerColor = Color(0xFF1A1A1A),
                            unfocusedContainerColor = Color(0xFF1A1A1A),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            var url = urlText.trim()
                            if (!url.startsWith("http")) url = "https://$url"
                            browserProvider.setUrl(url)
                            webViewRef?.loadUrl(url)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(YTDownPurple, shape = RoundedCornerShape(14.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ir", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (browserState.progress < 1.0) {
                LinearProgressIndicator(
                    progress = { browserState.progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = YTDownPurple,
                    trackColor = Color(0xFF1A1A1A)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    browserProvider.setLoading(true)
                                    browserProvider.setInitialLoad(false)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    browserProvider.setLoading(false)
                                    browserProvider.setUrl(url ?: browserState.currentUrl)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (view == null || request == null || error == null) return
                                    if (request.isForMainFrame != true) return

                                    if (browserState.isYoutube &&
                                        error.errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
                                        android.util.Log.d(
                                            "BrowserScreen",
                                            "⚠️ YouTube connection refused (ignoring): ${request.url}"
                                        )
                                        return
                                    }

                                    if (browserState.isInitialLoad) {
                                        browserProvider.setInitialLoad(false)
                                        return
                                    }

                                    if (browserState.hasShownError) return

                                    browserProvider.setHasShownError(true)
                                    Toast.makeText(
                                        context,
                                        "Erro ao carregar página: ${error.description}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (view == null || request == null || errorResponse == null) return
                                    if (request.isForMainFrame != true) return

                                    val url = request.url.toString()
                                    val statusCode = errorResponse.statusCode
                                    if (statusCode == 403 &&
                                        (url.contains("accounts.google.com") || url.contains("signin"))) {
                                        return
                                    }

                                    if (browserState.isInitialLoad) {
                                        browserProvider.setInitialLoad(false)
                                        return
                                    }

                                    if (browserState.hasShownError) return

                                    browserProvider.setHasShownError(true)
                                    Toast.makeText(
                                        context,
                                        "Erro HTTP $statusCode ao carregar a página",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    browserProvider.setProgress(newProgress / 100.0)
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            loadUrl(browserState.currentUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != browserState.currentUrl) {
                            webView.loadUrl(browserState.currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
