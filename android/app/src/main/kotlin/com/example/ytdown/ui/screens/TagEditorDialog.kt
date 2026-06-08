package com.example.ytdown.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ytdown.ui.theme.TextSecondary
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadType
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.ui.DownloadViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    viewModel: DownloadViewModel,
    onConfirm: (FilePath) -> Unit
) {
    val state by viewModel.inputState.collectAsState()
    if (!state.showDialog) return

    val context = LocalContext.current
    var publicFolder = Environment.DIRECTORY_MOVIES
    if (state.selectedDownloadType == DownloadType.AUDIO) {
        publicFolder = Environment.DIRECTORY_MUSIC
    }
    val downloadDir = Environment.getExternalStoragePublicDirectory(publicFolder)
        ?.let { File(it, "YTDown") }
        ?.also { if (!it.exists()) it.mkdirs() }
        ?.absolutePath
        ?: context.filesDir.absolutePath

    Dialog(onDismissRequest = { viewModel.onDismissDialog() }) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HeaderSection(state)
                MetadataSection(viewModel, state)
                FormatSelection(viewModel, state)
                PlaylistSection(viewModel, state)
                Text(
                    text = "Salvar em: $downloadDir",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(
                    onClick = { onConfirm(FilePath(downloadDir)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Iniciar Download")
                }
            }
        }
    }
}

@Composable
private fun MetadataSection(viewModel: DownloadViewModel, state: com.example.ytdown.ui.DownloadInputState) {
    MetadataFields(viewModel, state)
}

@Composable
private fun HeaderSection(state: com.example.ytdown.ui.DownloadInputState) {
    val firstItem = state.fetchedItems.firstOrNull()
    val headerTitle = firstItem?.title?.value ?: "Detalhes do vídeo"
    var playlistInfo: String? = null
    if (state.isPlaylist) {
        playlistInfo = "Playlist - ${state.fetchedItems.size} itens"
    }

    Row(modifier = Modifier.padding(bottom = 16.dp)) {
        AsyncImage(
            model = firstItem?.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(100.dp, 60.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )
            playlistInfo?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Capa gerada automaticamente via LastFM",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MetadataFields(viewModel: DownloadViewModel, state: com.example.ytdown.ui.DownloadInputState) {
    OutlinedTextField(
        value = state.titleInput,
        onValueChange = viewModel::onTitleInputChanged,
        label = { Text("Título da música") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
    OutlinedTextField(
        value = state.artistInput,
        onValueChange = viewModel::onArtistInputChanged,
        label = { Text("Artista") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
    OutlinedTextField(
        value = state.albumInput,
        onValueChange = viewModel::onAlbumInputChanged,
        label = { Text("Álbum") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormatSelection(viewModel: DownloadViewModel, state: com.example.ytdown.ui.DownloadInputState) {
    Text("Tipo e Formato", modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedDownloadType == DownloadType.AUDIO,
            onClick = { viewModel.onDownloadTypeSelected(DownloadType.AUDIO) },
            label = { Text("Áudio") }
        )
        FilterChip(
            selected = state.selectedDownloadType == DownloadType.VIDEO,
            onClick = { viewModel.onDownloadTypeSelected(DownloadType.VIDEO) },
            label = { Text("Vídeo") }
        )
    }

    var formats = state.videoFormats
    if (state.selectedDownloadType == DownloadType.AUDIO) {
        formats = state.audioFormats
    }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        formats.forEach { fmt ->
            FilterChip(
                selected = state.selectedFormat == fmt,
                onClick = { viewModel.onFormatSelected(fmt) },
                label = { Text(fmt.uppercase()) }
            )
        }
    }

    var qualities = state.videoResolutions
    if (state.selectedDownloadType == DownloadType.AUDIO) {
        qualities = state.audioBitrates
    }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        qualities.forEach { quality ->
            FilterChip(
                selected = state.selectedQuality == quality,
                onClick = { viewModel.onQualitySelected(quality) },
                label = { Text(quality.uppercase()) }
            )
        }
    }
}

@Composable
private fun PlaylistSection(viewModel: DownloadViewModel, state: com.example.ytdown.ui.DownloadInputState) {
    if (!state.isPlaylist) return

    TextButton(onClick = viewModel::onSelectAllItems) { Text("Selecionar Tudo") }
    LazyColumn(modifier = Modifier.height(200.dp)) {
        items(state.fetchedItems) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.onVideoSelected(item, !item.isSelected) },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.title.value, modifier = Modifier.weight(1f), maxLines = 1)
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { viewModel.onVideoSelected(item, it) }
                )
            }
        }
    }
}
