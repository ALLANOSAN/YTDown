package com.example.ytdown.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets // Este deve vir de foundation.layout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet // Certifique-se que termina em material3
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadType
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
        viewModel: DownloadViewModel,
        onDismiss: () -> Unit,
        onConfirm: (() -> Unit)? = null
) {
    val state by viewModel.inputState.collectAsStateWithLifecycle()
    val firstItem = state.fetchedItems.firstOrNull()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = Color(0xFF121212),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) },
            contentWindowInsets = { WindowInsets(0) }
    ) {
        if (firstItem != null) {
            Column(
                    modifier =
                            Modifier.padding(horizontal = 24.dp)
                                    .padding(bottom = 32.dp)
                                    .imePadding() // sobe o conteúdo quando o teclado aparece
                                    .fillMaxWidth()
                                    .verticalScroll(
                                            rememberScrollState()
                                    ) // permite rolar se teclado cobrir algo
            ) {
                // Preview do Item
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                            model = firstItem.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                                firstItem.title.value,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                        )
                        Text("Configurar Download", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Campos de Metadados
                OutlinedTextField(
                        value = state.titleInput,
                        onValueChange = viewModel::onTitleInputChanged,
                        label = { Text("Título da música", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = YTDownPurple,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = state.artistInput,
                        onValueChange = viewModel::onArtistInputChanged,
                        label = { Text("Artista", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors =
                                OutlinedTextFieldDefaults.colors(
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                                KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                ),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = YTDownPurple,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tipo de Download (Áudio/Vídeo)
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DownloadTypeCard(
                            title = "Áudio",
                            icon = Icons.Default.AudioFile,
                            isSelected = state.selectedDownloadType == DownloadType.AUDIO,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.onDownloadTypeSelected(DownloadType.AUDIO)
                                // Reset formato se o atual for incompatível com áudio
                                if (state.selectedFormat !in state.audioFormats) {
                                    viewModel.onFormatSelected("mp3")
                                }
                            }
                    )
                    DownloadTypeCard(
                            title = "Vídeo",
                            icon = Icons.Default.VideoFile,
                            isSelected = state.selectedDownloadType == DownloadType.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.onDownloadTypeSelected(DownloadType.VIDEO)
                                // Reset formato se o atual for incompatível com vídeo
                                if (state.selectedFormat !in state.videoFormats) {
                                    viewModel.onFormatSelected("mp4")
                                }
                            }
                    )
                }

                // Aviso de formato incompatível
                val audioFormats = state.audioFormats
                val videoFormats = state.videoFormats
                val isIncompatible =
                        (state.selectedDownloadType == DownloadType.AUDIO &&
                                state.selectedFormat in videoFormats) ||
                                (state.selectedDownloadType == DownloadType.VIDEO &&
                                        state.selectedFormat in audioFormats)
                if (isIncompatible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            color = Color(0xFF3D2800)
                    ) {
                        Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text =
                                            "Formato \"${state.selectedFormat.uppercase()}\" incompatível com ${
                                    if (state.selectedDownloadType == DownloadType.AUDIO) "Áudio" else "Vídeo"
                                }. Selecione um formato válido.",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Formatos e Qualidades
                Text("Formato", color = Color.White, fontWeight = FontWeight.Bold)
                LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                ) {
                    var formats = state.videoFormats
                    if (state.selectedDownloadType == DownloadType.AUDIO) {
                        formats = state.audioFormats
                    }
                    items(formats) { format ->
                        FilterChip(
                                selected = state.selectedFormat == format,
                                onClick = {
                                    viewModel.onFormatSelected(format)
                                    // lossless só para flac/wav — reset para 320 nos outros
                                    if (format == "wav" || format == "flac") {
                                        viewModel.onQualitySelected("lossless")
                                    } else if (state.selectedQuality == "lossless") {
                                        viewModel.onQualitySelected("320")
                                    }
                                },
                                label = { Text(format.uppercase()) },
                                colors =
                                        FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = YTDownPurple,
                                                labelColor = Color.White,
                                                selectedLabelColor = Color.White
                                        )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Qualidade — bitrate para áudio, resolução para vídeo
                val qualityLabel =
                        if (state.selectedDownloadType == DownloadType.AUDIO) "Bitrate"
                        else "Resolução"
                val qualityOptions =
                        if (state.selectedDownloadType == DownloadType.AUDIO) state.audioBitrates
                        else state.videoResolutions

                Text(qualityLabel, color = Color.White, fontWeight = FontWeight.Bold)
                LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(qualityOptions) { quality ->
                        val isLossless = quality == "lossless"
                        val isDisabled =
                                isLossless &&
                                        state.selectedFormat != "flac" &&
                                        state.selectedFormat != "wav"

                        FilterChip(
                                selected = state.selectedQuality == quality,
                                enabled = !isDisabled,
                                onClick = { viewModel.onQualitySelected(quality) },
                                label = {
                                    Text(
                                            if (state.selectedDownloadType == DownloadType.AUDIO &&
                                                            !isLossless
                                            )
                                                    "${quality}kbps"
                                            else quality.uppercase()
                                    )
                                },
                                colors =
                                        FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = YTDownPurple,
                                                labelColor = Color.White,
                                                selectedLabelColor = Color.White,
                                                disabledLabelColor = Color.Gray
                                        )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val context = LocalContext.current
                var publicFolder = Environment.DIRECTORY_MOVIES
                if (state.selectedDownloadType == DownloadType.AUDIO) {
                    publicFolder = Environment.DIRECTORY_MUSIC
                }
                val downloadFolder =
                        context.getExternalFilesDir(publicFolder)
                                ?.let { File(it, "YTDown") }
                                ?.also { if (!it.exists()) it.mkdirs() }
                                ?.absolutePath
                                ?: File(context.filesDir, "YTDown")
                                        .also { if (!it.exists()) it.mkdirs() }
                                        .absolutePath

                Text(
                        text = "Salvar em: $downloadFolder",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                )

                val formatIsValid =
                        (state.selectedDownloadType == DownloadType.AUDIO &&
                                state.selectedFormat in state.audioFormats) ||
                                (state.selectedDownloadType == DownloadType.VIDEO &&
                                        state.selectedFormat in state.videoFormats)

                Button(
                        onClick = {
                            if (downloadFolder.isNotBlank()) {
                                viewModel.startDownloadFlow(FilePath(downloadFolder))
                            }
                            onDismiss()
                            onConfirm?.invoke()
                        },
                        enabled = formatIsValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple),
                        shape = RoundedCornerShape(16.dp)
                ) { Text("Iniciar Download", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
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
    var surfaceColor = SurfaceDark
    if (isSelected) {
        surfaceColor = YTDownPurple
    }
    var borderStroke: androidx.compose.foundation.BorderStroke? =
            androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
    if (isSelected) {
        borderStroke = null
    }

    Surface(
            modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() },
            color = surfaceColor,
            border = borderStroke
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var iconTint = TextSecondary
            if (isSelected) {
                iconTint = Color.White
            }
            Icon(icon, null, tint = iconTint)
            Spacer(modifier = Modifier.height(8.dp))
            var titleColor = TextSecondary
            if (isSelected) {
                titleColor = Color.White
            }
            Text(title, color = titleColor, fontWeight = FontWeight.Bold)
        }
    }
}
