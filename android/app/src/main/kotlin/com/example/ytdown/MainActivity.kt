package com.example.ytdown

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.ytdown.services.SharingIntentService
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.RootApp
import com.example.ytdown.ui.theme.YTDownTheme
import com.example.ytdown.ui.theme.YTDownPurple
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()
    
    @Inject
    lateinit var sharingIntentService: SharingIntentService
    
    private var isRuntimeReady by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("MainActivity", "Permissão de notificação concedida: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ DIAGNÓSTICO DE INICIALIZAÇÃO (CHAQUOPY_DEBUG)
        android.util.Log.d("CHAQUOPY_DEBUG", "--------------------------------------------------")
        android.util.Log.d("CHAQUOPY_DEBUG", "🔍 Iniciando Diagnóstico Técnico do Sistema")
        
        PythonBridge.initializePython(this)
        
        try {
            val py = com.chaquo.python.Python.getInstance()
            val sys = py.getModule("sys")
            android.util.Log.d("CHAQUOPY_DEBUG", "✅ Python Version: ${sys["version"]}")
            
            val ytdown = py.getModule("ytdown")
            android.util.Log.d("CHAQUOPY_DEBUG", "✅ Módulo 'ytdown' carregado com sucesso.")
            
            // 1. Verifica se o yt-dlp está acessível
            try {
                py.getModule("yt_dlp")
                android.util.Log.d("CHAQUOPY_DEBUG", "✅ Biblioteca 'yt-dlp' encontrada.")
            } catch (e: Exception) {
                android.util.Log.e("CHAQUOPY_DEBUG", "⚠️ Biblioteca 'yt-dlp' não encontrada via import direto.")
            }

            // 2. Verifica conectividade básica de rede via Python
            try {
                val socket = py.getModule("socket")
                android.util.Log.d("CHAQUOPY_DEBUG", "🌐 Testando resolução de DNS (google.com)...")
                val ip = socket.callAttr("gethostbyname", "google.com").toString()
                android.util.Log.d("CHAQUOPY_DEBUG", "✅ DNS OK: google.com -> $ip")
            } catch (e: Exception) {
                android.util.Log.e("CHAQUOPY_DEBUG", "❌ Falha crítica de REDE/DNS no Python: ${e.message}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("CHAQUOPY_DEBUG", "❌ Falha no diagnóstico de inicialização: ${e.message}")
        }
        android.util.Log.d("CHAQUOPY_DEBUG", "--------------------------------------------------")

        isRuntimeReady = true

        setContent {
            YTDownTheme {
                if (isRuntimeReady) {
                    RootApp(viewModel = viewModel)
                } else {
                    LoadingScreen()
                }
            }
        }

        handleIntent(intent)
        checkAndRequestPermissions()
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        sharingIntentService.handleIntent(intent)?.let { cleanedUrl ->
            viewModel.onUrlInputChanged(cleanedUrl)
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = YTDownPurple)
    }
}
