package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seção yt-dlp
            SettingsCard(
                title = "Motor de Download (yt-dlp)",
                icon = Icons.Default.SettingsInputComponent
            ) {
                Text("Versão Atual: ${state.ytDlpVersion}", color = TextSecondary, fontSize = 14.sp)
                Text("Versão PyPI: ${state.latestVersion}", color = TextSecondary, fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { viewModel.updateYtDlp() },
                    enabled = !state.isUpdating && !state.isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) {
                    if (state.isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    }
                    if (!state.isUpdating) {
                        Text("Atualizar Motor")
                    }
                }
            }

            // Seção Manutenção
            SettingsCard(
                title = "Manutenção da Biblioteca",
                icon = Icons.Default.Build
            ) {
                if (state.isRepairing) {
                    LinearProgressIndicator(
                        progress = { state.repairProgress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = YTDownPurple
                    )
                    Text("Processando: ${(state.repairProgress * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                MaintenanceButton(
                    text = "Regravar Tags ID3 Físicas",
                    icon = Icons.AutoMirrored.Filled.Label,
                    onClick = { viewModel.repairAllMetadata() },
                    enabled = !state.isRepairing
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                MaintenanceButton(
                    text = "Enriquecer Capas em Lote",
                    icon = Icons.Default.Image,
                    onClick = { viewModel.enrichAllArtwork() },
                    enabled = !state.isRepairing
                )
            }

            SettingsCard(
                title = "Diagnóstico de Downloads",
                icon = Icons.Default.BugReport
            ) {
                Text(
                    "Veja falhas recentes de download e tente recuperá-las.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) {
                    Text("Abrir Diagnóstico")
                }
            }

            // Mensagens
            state.lastMessage?.let {
                Text(it, color = YTDownPurple, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = YTDownPurple, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun MaintenanceButton(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
