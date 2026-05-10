package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.ui.DiscoveryViewModel
import com.example.ytdown.ui.MetalDiscoveryViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@Composable
fun MetalDiscoveryScreen(
    viewModel: MetalDiscoveryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.suggestions.isEmpty()) {
            viewModel.loadSuggestions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Descoberta Metal",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            IconButton(onClick = { viewModel.loadSuggestions() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = YTDownPurple)
            }
        }

        Text(
            "Baseado na sua biblioteca (Metal-Archives)",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YTDownPurple)
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = Color.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.suggestions) { band ->
                    DiscoveryBandRow(band = band, onDownload = { viewModel.downloadBand(band.name) })
                }
            }
        }
    }
}

@Composable
fun DiscoveryBandRow(
    band: com.example.ytdown.services.MetalBand,
    onDownload: () -> Unit
) {
    var downloadTriggered by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    band.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    band.genre ?: "Metal",
                    color = YTDownPurple,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    band.country ?: "Desconhecido",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            
            Button(
                onClick = { 
                    onDownload()
                    downloadTriggered = true
                },
                enabled = !downloadTriggered,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (downloadTriggered) Color.DarkGray else YTDownPurple
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Download, 
                    contentDescription = null, 
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (downloadTriggered) "Na Fila" else "Baixar", fontSize = 12.sp)
            }
        }
    }
}
