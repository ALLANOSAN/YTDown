package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    title: String,
    viewModel: DownloadViewModel,
    systemViewModel: SystemViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onBack: () -> Unit
) {
    val allItems by viewModel.downloads.collectAsState()
    val groupItems = allItems.filter {
        it.album == title || it.artist == title || it.folderName == title
    }.filter { it.status == "completed" }

    val playlists by systemViewModel.playlists.collectAsState()

    var trackToEdit by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var trackForPlaylist by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var showTrackMenu by remember { mutableStateOf<DownloadItemEntity?>(null) }

    val artwork = groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl
        ?: groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groupItems) { song ->
                    PlaylistDetailSongRow(
                        song = song,
                        onClick = {
                            playerViewModel.playTrack(song)
                            onNavigateToPlayer()
                        },
                        onLongClick = { showTrackMenu = song }
                    )
                }
            }
        }
    }

    showTrackMenu?.let { song ->
        AlertDialog(
            onDismissRequest = { showTrackMenu = null },
            containerColor = SurfaceDark,
            title = { Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Adicionar à Playlist") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = YTDownPurple) },
                        modifier = Modifier.clickable {
                            trackForPlaylist = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                    )
                    ListItem(
                        headlineContent = { Text("Editar Nome") },
                        leadingContent = { Icon(Icons.Default.Edit, null, tint = YTDownPurple) },
                        modifier = Modifier.clickable {
                            trackToEdit = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                    )
                }
            },
            confirmButton = {}
        )
    }

    trackForPlaylist?.let { song ->
        PlaylistSelectionDialog(
            playlists = playlists,
            onDismiss = { trackForPlaylist = null },
            onSelect = { playlist ->
                systemViewModel.addTrackToPlaylist(playlist.playlist.id, song.id)
                trackForPlaylist = null
            }
        )
    }

    trackToEdit?.let { song ->
        EditTrackNameDialog(
            song = song,
            onDismiss = { trackToEdit = null },
            onSave = { newName ->
                systemViewModel.updateTrackName(song, newName)
                trackToEdit = null
            }
        )
    }
}

@Composable
private fun PlaylistDetailSongRow(
    song: DownloadItemEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailPath,
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(song.artist ?: "Desconhecido", color = TextSecondary, fontSize = 12.sp)
        }

        IconButton(onClick = onLongClick) {
            Icon(Icons.Default.MoreVert, null, tint = TextSecondary)
        }
    }
}

@Composable
fun PlaylistSelectionDialog(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistWithCount) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Adicionar à Playlist", color = Color.White) },
        text = {
            if (playlists.isEmpty()) {
                Text("Crie uma playlist primeiro na aba Playlists.", color = TextSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} músicas") },
                            modifier = Modifier.clickable { onSelect(playlist) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EditTrackNameDialog(
    song: DownloadItemEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(song.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Editar Nome", color = Color.White) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome da Música") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
