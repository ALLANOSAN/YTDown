package com.example.ytdown.ui.screens

import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.core.domain.DownloadType
import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.providers.browserProvider
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
        viewModel: DownloadViewModel = hiltViewModel(),
        onUrlRequest: (VideoUrl) -> Unit
) {
    val browserState by browserProvider.state.collectAsStateWithLifecycle()
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    var urlText by rememberSaveable { mutableStateOf(browserState.currentUrl) }
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(browserState.currentUrl))
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Seleciona todo o texto sempre que o campo ganha foco
    LaunchedEffect(isFocused) {
        if (isFocused) {
            textFieldValue = textFieldValue.copy(
                selection = TextRange(0, textFieldValue.text.length)
            )
        }
    }

    LaunchedEffect(browserState.currentUrl) {
        urlText = browserState.currentUrl
        textFieldValue = TextFieldValue(browserState.currentUrl)
    }

    fun updateNavigationState() {
        webViewRef?.let {
            canGoBack = it.canGoBack()
            canGoForward = it.canGoForward()
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    Scaffold(
            // Scaffold aninhado: o de RootApp.kt ja consome os insets das barras
            // do sistema. Sem zerar, o padding entra duas vezes. A altura da
            // topBar continua vindo no `padding` normalmente.
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                        title = { Text("Navegador", color = Color.White) },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
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
                                if (!browserState.isLoading)
                                        onUrlRequest(VideoUrl(browserState.currentUrl))
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
                        } else {
                            Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Baixar"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Baixar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            containerColor = SurfaceDark
    ) { padding ->
        val context = LocalContext.current
        if (inputState.showDialog) {
            TagEditorDialog(
                    viewModel = viewModel,
                    onConfirm = { folder ->
                        viewModel.startDownloadFlow(folder)
                    }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Voltar
                    IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = if (canGoBack) Color.White else Color.Gray
                        )
                    }

                    // Botão Avançar
                    IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Avançar",
                                tint = if (canGoForward) Color.White else Color.Gray
                        )
                    }

                    // Botão Recarregar
                    IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Recarregar",
                                tint = Color.White
                        )
                    }

                    OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { tv ->
                                textFieldValue = tv
                                urlText = tv.text
                            },
                            modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { state ->
                                        isFocused = state.isFocused
                                    },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                    onGo = {
                                        var url = textFieldValue.text.trim()
                                        if (!url.startsWith("http")) url = "https://$url"
                                        browserProvider.setUrl(url)
                                        webViewRef?.loadUrl(url)
                                    }
                            ),
                            placeholder = {
                                Text("Pesquisar ou digitar URL", color = TextSecondary)
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedBorderColor = YTDownPurple,
                                            focusedContainerColor = Color(0xFF1A1A1A),
                                            unfocusedContainerColor = Color(0xFF1A1A1A),
                                            focusedLabelColor = Color.White,
                                            unfocusedLabelColor = TextSecondary
                                    )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                            onClick = {
                                var url = textFieldValue.text.trim()
                                if (!url.startsWith("http")) url = "https://$url"
                                browserProvider.setUrl(url)
                                webViewRef?.loadUrl(url)
                            },
                            modifier =
                                    Modifier.size(40.dp)
                                            .background(
                                                    YTDownPurple,
                                                    shape = RoundedCornerShape(12.dp)
                                            )
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Ir",
                                tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (browserState.progress < 1.0) {
                LinearProgressIndicator(
                        progress = { browserState.progress.toFloat() },
                        modifier =
                                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
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
                                webViewClient =
                                        object : WebViewClient() {
                                            override fun onPageStarted(
                                                    view: WebView?,
                                                    url: String?,
                                                    favicon: android.graphics.Bitmap?
                                            ) {
                                                browserProvider.setLoading(true)
                                                browserProvider.setInitialLoad(false)
                                                // Atualiza URL já no início — cobre navegação
                                                // normal
                                                url?.let { browserProvider.setUrl(it) }
                                            }

                                            override fun onPageFinished(
                                                    view: WebView?,
                                                    url: String?
                                            ) {
                                                browserProvider.setLoading(false)
                                                updateNavigationState()
                                                browserProvider.setUrl(
                                                        url ?: browserState.currentUrl
                                                )
                                            }

                                            // ← CHAVE DO FIX: dispara em navegação JavaScript
                                            // (SPA/pushState)
                                            // O YouTube mobile troca de URL sem recarregar a página
                                            // inteira.
                                            // onPageFinished NÃO dispara nesses casos — só este
                                            // callback dispara.
                                            override fun doUpdateVisitedHistory(
                                                    view: WebView?,
                                                    url: String?,
                                                    isReload: Boolean
                                            ) {
                                                super.doUpdateVisitedHistory(view, url, isReload)
                                                if (!isReload && url != null) {
                                                    browserProvider.setUrl(url)
                                                    updateNavigationState()
                                                }
                                            }
                                        }
                                webChromeClient =
                                        object : WebChromeClient() {
                                            override fun onProgressChanged(
                                                    view: WebView?,
                                                    newProgress: Int
                                            ) {
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
                        update = {},
                        modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun DownloadConfigDialog(
        state: com.example.ytdown.ui.DownloadInputState,
        onDismiss: () -> Unit,
        onStart: () -> Unit,
        onTypeSelected: (DownloadType) -> Unit
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Configurar Download", color = Color.White) },
            text = {
                Column {
                    Text(
                            "Deseja baixar como ${if (state.isPlaylist) "playlist" else "vídeo"}?",
                            color = Color.White
                    )
                }
            },
            confirmButton = { TextButton(onClick = onStart) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
