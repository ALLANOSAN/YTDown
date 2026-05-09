package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.R
import com.example.ytdown.services.StorageService
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        viewModel: SystemViewModel,
        onBack: () -> Unit,
        onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // ✅ FIX: registra o launcher moderno para o SAF file picker.
    // Quando StorageService não consegue exportar via MediaStore, emite um
    // SafPickerRequest — este launcher abre o seletor de arquivo nativo do Android.
    val safPickerRequest: StorageService.Companion.SafPickerRequest? by viewModel.safPickerRequest.collectAsStateWithLifecycle()
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            safPickerRequest?.mimeType ?: "*/*"
        )
    ) { uri ->
        uri?.let { StorageService.getInstance().completeSafExport(context, it) }
        viewModel.clearSafPickerRequest()
    }

    // Lança o picker quando há uma solicitação pendente
    LaunchedEffect(safPickerRequest) {
        safPickerRequest?.let { req ->
            safLauncher.launch(req.displayName)
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(R.string.settings_title), color = Color.White, style = MaterialTheme.typography.titleLarge) },
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
                modifier =
                        Modifier.padding(padding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- BENTO GRID LAYOUT ---

            // Row 1: Motor de Download (Destaque)
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.settings_engine_title),
                subtitle = "yt-dlp v${state.ytDlpVersion}",
                icon = Icons.Default.SettingsInputComponent,
                color = YTDownPurple.copy(alpha = 0.1f)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = YTDownPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (state.isUpdating) "Atualizando para v${state.latestVersion}..." else "Versão PyPI: ${state.latestVersion}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            if (state.lastMessage?.contains("Nova versão disponível", ignoreCase = true) == true) {
                                viewModel.updateYtDlp()
                            } else {
                                viewModel.refreshYtDlpVersion(forceNetwork = true)
                            }
                        },
                        enabled = !state.isUpdating && !state.isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.lastMessage?.contains("Nova versão disponível", ignoreCase = true) == true) Color(0xFF388E3C) else YTDownPurple
                        )
                    ) {
                        Text(if (state.lastMessage?.contains("Nova versão disponível", ignoreCase = true) == true) "Instalar Atualização" else "Verificar Atualização")
                    }
                }
            }

            // Row 2: Bento Grid (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Manutenção
                BentoCard(
                    modifier = Modifier.weight(1f).height(200.dp),
                    title = stringResource(R.string.settings_repair_tags),
                    subtitle = "ID3 & Metadados",
                    icon = Icons.AutoMirrored.Filled.Label,
                    onClick = { if (!state.isRepairing) viewModel.repairAllMetadata() }
                ) {
                    if (state.isRepairing) {
                        LinearProgressIndicator(
                            progress = { state.repairProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = YTDownPurple
                        )
                    }
                }

                // Diagnóstico
                BentoCard(
                    modifier = Modifier.weight(1f).height(200.dp),
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = "Logs de Falhas",
                    icon = Icons.Default.BugReport,
                    onClick = onNavigateToDiagnostics,
                    color = Color(0xFFD32F2F).copy(alpha = 0.1f)
                )
            }

            // Row 3: Exportação (Full width)
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.settings_export_library),
                subtitle = "Salvar na pasta pública",
                icon = Icons.Default.FolderOpen,
                color = Color(0xFF388E3C).copy(alpha = 0.1f)
            ) {
                Column {
                    Text(
                        "Copia os arquivos concluídos para as pastas de Música e Vídeo do celular.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (state.isExporting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.exportProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = YTDownPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.exportAllToPublicFolders() },
                        enabled = !state.isExporting && !state.isRepairing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                    ) {
                        Text(if (state.isExporting) "Exportando..." else stringResource(R.string.action_export))
                    }
                }
            }

            // Row 4: Enriquecimento
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.settings_enrich_artwork),
                subtitle = "Busca automática de fotos",
                icon = Icons.Default.AutoFixHigh,
                onClick = { if (!state.isRepairing) viewModel.enrichAllArtwork() }
            )

            // Mensagens
            state.lastMessage?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = YTDownPurple.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        it,
                        color = YTDownPurple,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color = SurfaceDark,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = SurfaceDark,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color, SurfaceDark)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Icon(
                    icon,
                    null,
                    tint = if (color != SurfaceDark) color.copy(alpha = 1f) else YTDownPurple,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 18.sp
                )
                Text(
                    subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 13.sp
                )
                if (content != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}
