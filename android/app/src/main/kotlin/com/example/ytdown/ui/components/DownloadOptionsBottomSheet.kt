package com.example.ytdown.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.VideoFile
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
import com.example.ytdown.core.domain.DownloadType
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    viewModel: DownloadViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.inputState.collectAsState()
    val firstItem = state.fetchedItems.firstOrNull() ?: return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            // Preview do Item
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = firstItem.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(firstItem.title.value, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("Configurar Download", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Campos de Metadados
            OutlinedTextField(
                value = state.artistInput,
                onValueChange = viewModel::onArtistInputChanged,
                label = { Text("Artista", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YTDownPurple,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.albumInput,
                onValueChange = viewModel::onAlbumInputChanged,
                label = { Text("Álbum", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YTDownPurple,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tipo de Download (Áudio/Vídeo)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DownloadTypeCard(
                    title = "Áudio",
                    icon = Icons.Default.AudioFile,
                    isSelected = state.selectedDownloadType == DownloadType.AUDIO,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.onDownloadTypeSelected(DownloadType.AUDIO) }
                )
                DownloadTypeCard(
                    title = "Vídeo",
                    icon = Icons.Default.VideoFile,
                    isSelected = state.selectedDownloadType == DownloadType.VIDEO,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.onDownloadTypeSelected(DownloadType.VIDEO) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Formatos e Qualidades
            Text("Formato", color = Color.White, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                val formats = if (state.selectedDownloadType == DownloadType.AUDIO) state.audioFormats else state.videoFormats
                items(formats) { format ->
                    FilterChip(
                        selected = state.selectedFormat == format,
                        onClick = { viewModel.onFormatSelected(format) },
                        label = { Text(format.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YTDownPurple,
                            labelColor = Color.White,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { 
                    viewModel.startDownloadFlow(FilePath("/storage/emulated/0/Music/YTDown"))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Iniciar Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun DownloadTypeCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = if (isSelected) YTDownPurple else SurfaceDark,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = if (isSelected) Color.White else TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = if (isSelected) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}
