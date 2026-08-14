package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.providers.DownloadDiagnosticsProvider
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DownloadDiagnosticsProvider = hiltViewModel()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val diagnosticsStats by viewModel.diagnosticsStats.collectAsState()

    val totalDownloads = (diagnosticsStats["total"] as? Int) ?: 0
    val completedDownloads = (diagnosticsStats["completed"] as? Int) ?: 0
    val failedCount = (diagnosticsStats["failed"] as? Int) ?: 0

    Scaffold(
        // Scaffold aninhado: o de RootApp.kt ja consome os insets das barras
        // do sistema. Sem zerar, o padding entra duas vezes. A altura da
        // topBar continua vindo no `padding` normalmente.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de Downloads", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SummaryRow(total = totalDownloads, completed = completedDownloads, failed = failedCount)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Os registros de diagnóstico também são enviados para o Firebase Crashlytics.",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (failedDownloads.isEmpty()) {
                NoFailuresMessage()
            } else {
                Button(
                    onClick = { viewModel.clearFailedLogs() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) {
                    Text("Limpar logs de falha")
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(failedDownloads, key = { it.id }) { item ->
                        FailedDownloadCard(item = item, onRetry = { viewModel.retryDownload(item) })
                    }
                }
            }
        }
    }
}
@Composable
private fun SummaryRow(total: Int, completed: Int, failed: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryColumn(label = "Total", value = total)
            SummaryColumn(label = "Concluídos", value = completed)
            SummaryColumn(label = "Falhas", value = failed)
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun NoFailuresMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Nenhuma falha recente encontrada.", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun FailedDownloadCard(item: DownloadItemEntity, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(item.url, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Caminho: ${item.outputPath}", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) {
                Text("Tentar novamente")
            }
        }
    }
}
