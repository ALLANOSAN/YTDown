package com.example.ytdown

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.*
import com.example.ytdown.ui.theme.YTDownTheme
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.screens.DownloadListScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var orchestrator: BinaryOrchestrator
    private val viewModel: DownloadViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> 
        // Permissões concedidas ou negadas - o WorkManager lidará com a falha se necessário
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        handleSharedIntent(intent)

        // Garantir que os binários estão prontos antes da UI pesada
        lifecycleScope.launch {
            orchestrator.setupPythonRuntime(AssetPath("python/python_runtime.tar.gz"))
            
            setContent {
                YTDownTheme {
                    DownloadListScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_SEND) return
        
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        viewModel.onUrlInputChanged(sharedText) // Define a URL no estado de input
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Adicionar outras conforme necessário (ex: WRITE_EXTERNAL_STORAGE para APIs antigas)
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}