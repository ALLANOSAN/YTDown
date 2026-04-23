package com.example.ytdown

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.ytdown.core.domain.AssetPath
import com.example.ytdown.core.infrastructure.BinaryOrchestrator
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.RootApp
import com.example.ytdown.ui.theme.YTDownTheme
import com.example.ytdown.ui.theme.YTDownPurple
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var orchestrator: BinaryOrchestrator
    private val viewModel: DownloadViewModel by viewModels()
    
    private var isRuntimeReady by mutableStateOf(false)

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onArtworkSelected(it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            orchestrator.setupPythonRuntime(AssetPath("python/python_runtime.bin"))
            isRuntimeReady = true
        }

        setContent {
            YTDownTheme {
                if (isRuntimeReady) {
                    RootApp(
                        viewModel = viewModel,
                        onPickImage = { imagePickerLauncher.launch("image/*") }
                    )
                } else {
                    LoadingScreen()
                }
            }
        }

        handleSharedIntent(intent)
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.WAKE_LOCK
        )
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        requestPermissions(permissions.toTypedArray(), 1001)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { 
                viewModel.onUrlInputChanged(it)
            }
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