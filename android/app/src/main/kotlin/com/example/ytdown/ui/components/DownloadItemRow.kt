package com.example.ytdown.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.services.ProgressBus
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemRow(
    item: DownloadItemEntity,
    progressBus: ProgressBus? = null, // Injetado via LocalProvider ou parâmetro
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onExport: () -> Unit = {},
    onEditMetadata: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Estado de progresso local para 60FPS (Migrado do Flutter itemProgressProvider)
    var liveProgress by remember(item.id) { mutableStateOf(item.progress.toFloat()) }

    // Escuta o barramento de progresso em tempo real
    if ((item.status == "downloading" || item.status == "pending") && progressBus != null) {
        LaunchedEffect(item.id) {
            progressBus.updates
                .filter { it.id == item.id }
                .collect { update ->
                    liveProgress = update.progress.toFloat() / 100f
                }
        }
    }

    var surfaceColor = SurfaceDark
    if (isSelected) {
        surfaceColor = YTDownPurple.copy(alpha = 0.2f)
    }
    var surfaceBorder: androidx.compose.foundation.BorderStroke? = null
    if (isSelected) {
        surfaceBorder = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 2.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = surfaceColor,
        border = surfaceBorder
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = YTDownPurple)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            AsyncImage(
                model = item.albumArtPath,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                var displayTitle = item.title
                if (item.title.length > 40) {
                    displayTitle = "${item.title.take(37)}..."
                }
                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var icon = Icons.Default.Videocam
                    if (item.type == 0) {
                        icon = Icons.Default.MusicNote
                    }
                    Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.artist ?: "Desconhecido",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            when (item.status) {
                "pending" -> {
                    // Na fila aguardando — spinner indeterminado
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = TextSecondary,
                            strokeWidth = 2.dp
                        )
                        Text("Na fila", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                "downloading" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { liveProgress },
                            modifier = Modifier.size(28.dp),
                            color = YTDownPurple,
                            strokeWidth = 3.dp
                        )
                        Text(
                            "${(liveProgress * 100).toInt()}%",
                            color = YTDownPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                "completed" -> {
                    if (!isSelectionMode) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = TextSecondary)
                        }
                    }
                    if (isSelectionMode) {
                        Icon(Icons.Default.CheckCircle, null, tint = YTDownPurple, modifier = Modifier.size(24.dp))
                    }
                }
                "failed" -> {
                    Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Exportar para Downloads") },
                    onClick = {
                        showMenu = false
                        onExport()
                    },
                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) }
                )
                DropdownMenuItem(
                    text = { Text("Editar Metadados") },
                    onClick = {
                        showMenu = false
                        onEditMetadata()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Excluir", color = Color.Red) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                )
            }
        }
    }
}
