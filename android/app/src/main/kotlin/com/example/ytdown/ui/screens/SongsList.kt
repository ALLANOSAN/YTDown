package com.example.ytdown.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.components.StaggeredVerticalEntrance
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsList(
    songs: List<DownloadItemEntity>,
    recentlyAdded: List<DownloadItemEntity>,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onAddToPlaylist: (DownloadItemEntity) -> Unit,
    onEditName: ((DownloadItemEntity) -> Unit)? = null,
    onSuperFix: ((DownloadItemEntity) -> Unit)? = null
) {
    var songMenu by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var editingSong by remember { mutableStateOf<DownloadItemEntity?>(null) }

    if (songs.isEmpty()) {
        EmptyLibraryMessage()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (recentlyAdded.isNotEmpty() && songs.size > 10) {
            item {
                Text("Adicionadas Recentemente", color = YTDownPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    recentlyAdded.take(10).forEach { song ->
                        RecentSongCard(song = song, onClick = {
                            libraryViewModel.triggerHapticClick()
                            playerViewModel.playTrack(song)
                            onNavigateToPlayer()
                        })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Todas as Músicas", color = YTDownPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        itemsIndexed(items = songs, key = { _, s -> s.id }) { index, song ->
            StaggeredVerticalEntrance(index = index) {
                DownloadItemRow(
                    item = song,
                    onClick = {
                        libraryViewModel.triggerHapticClick()
                        playerViewModel.playPlaylist(songs, index)
                        onNavigateToPlayer()
                    },
                    onLongClick = {
                        libraryViewModel.triggerHapticHeavy()
                        songMenu = song
                    }
                )
            }
        }
    }

    songMenu?.let { song ->
        AlertDialog(
            onDismissRequest = { songMenu = null },
            containerColor = SurfaceDark,
            title = { Text(song.title, color = Color.White, fontSize = 15.sp, maxLines = 2) },
            text = {
                Column {
                    if (onEditName != null) {
                        ListItem(
                            headlineContent = { Text("Editar Nome") },
                            leadingContent = { Icon(Icons.Default.Edit, null, tint = YTDownPurple) },
                            modifier = Modifier.clickable { editingSong = song; songMenu = null },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Adicionar à Playlist") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = YTDownPurple) },
                        modifier = Modifier.clickable { onAddToPlaylist(song); songMenu = null },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                    )
                    if (onSuperFix != null) {
                        ListItem(
                            headlineContent = { Text("Correção Inteligente (MA)") },
                            leadingContent = { Icon(Icons.Default.AutoFixHigh, null, tint = YTDownPurple) },
                            modifier = Modifier.clickable { onSuperFix(song); songMenu = null },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = Color.White)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Dialog inline de edição de nome
    editingSong?.let { song ->
        var newTitle by remember(song.id) { mutableStateOf(song.title) }
        AlertDialog(
            onDismissRequest = { editingSong = null },
            containerColor = SurfaceDark,
            title = { Text("Editar Nome", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YTDownPurple,
                        unfocusedBorderColor = SurfaceDark,
                        focusedLabelColor = YTDownPurple,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onEditName?.invoke(song.copy(title = newTitle))
                            editingSong = null
                        }
                    },
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { editingSong = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecentSongCard(song: DownloadItemEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(100.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = song.thumbnailPath,
            contentDescription = null,
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = song.title, color = Color.White, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}
