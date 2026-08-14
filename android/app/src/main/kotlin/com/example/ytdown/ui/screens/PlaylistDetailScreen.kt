package com.example.ytdown.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

/**
 * Tela de detalhe unificada — serve para:
 *   • Artista  (title = nome do artista)
 *   • Álbum    (title = nome do álbum)
 *   • Playlist (title = id da playlist — isPlaylistId = true)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    title: String,
    viewModel: DownloadViewModel,
    systemViewModel: SystemViewModel,
    playbackViewModel: PlaybackViewModel,
    onNavigateToPlayer: () -> Unit,
    onBack: () -> Unit,
    isPlaylistId: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val allItems: List<DownloadItemEntity> by viewModel.allDownloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by systemViewModel.playlists.collectAsStateWithLifecycle(initialValue = emptyList())

    // Descobre o nome da playlist se estivermos no modo playlist-por-ID
    val playlistName = if (isPlaylistId) {
        playlists.firstOrNull { it.playlist.id == title }?.playlist?.name ?: title
    } else title

    // Filtra as músicas dependendo do modo
    val groupItems: List<DownloadItemEntity> = if (isPlaylistId) {
        // Carrega as faixas da playlist via Flow reativo
        val playlistTracks by systemViewModel.getPlaylistTracksFlow(title)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        playlistTracks
    } else {
        allItems.filter {
            (it.album == title || it.artist == title) && it.status == "completed"
        }
    }

    val artwork = groupItems.firstOrNull { !it.albumArtPath.isNullOrEmpty() }?.albumArtPath

    var showTrackMenu by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var trackForPlaylist by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var trackToEdit by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var trackToEditArtist by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var trackToEditAlbum by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        // Scaffold aninhado: o de RootApp.kt ja consome os insets das barras
        // do sistema. Sem zerar, o padding entra duas vezes. A altura da
        // topBar continua vindo no `padding` normalmente.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(playlistName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    // Botão deletar playlist (só aparece em playlists)
                    if (isPlaylistId) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
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
        ) {
            // Capa do grupo
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            if (groupItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma música aqui ainda.", color = TextSecondary, fontSize = 16.sp)
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items = groupItems, key = { _, s -> s.id }) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Toca a fila completa a partir do item clicado
                                    playbackViewModel.playPlaylist(groupItems, index)
                                    onNavigateToPlayer()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showTrackMenu = song
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.albumArtPath,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                song.artist ?: "Desconhecido",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Menu de long press ---
    showTrackMenu?.let { song ->
        AlertDialog(
            onDismissRequest = { showTrackMenu = null },
            containerColor = SurfaceDark,
            title = {
                Text(song.title, color = Color.White, fontSize = 15.sp, maxLines = 2)
            },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Adicionar à Playlist") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = YTDownPurple)
                        },
                        modifier = Modifier.clickable {
                            trackForPlaylist = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White
                        )
                    )
                    if (isPlaylistId) {
                        ListItem(
                            headlineContent = { Text("Remover da Playlist") },
                            leadingContent = {
                                Icon(Icons.Default.RemoveCircleOutline, null, tint = Color(0xFFFF6B6B))
                            },
                            modifier = Modifier.clickable {
                                systemViewModel.removeTrackFromPlaylist(title, song.id)
                                showTrackMenu = null
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = Color.White
                            )
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Editar Nome") },
                        leadingContent = {
                            Icon(Icons.Default.Edit, null, tint = YTDownPurple)
                        },
                        modifier = Modifier.clickable {
                            trackToEdit = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White
                        )
                    )
                    ListItem(
                        headlineContent = { Text("Editar Artista") },
                        leadingContent = {
                            Icon(Icons.Default.Person, null, tint = YTDownPurple)
                        },
                        modifier = Modifier.clickable {
                            trackToEditArtist = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White
                        )
                    )
                    ListItem(
                        headlineContent = { Text("Editar Álbum") },
                        leadingContent = {
                            Icon(Icons.Default.Album, null, tint = YTDownPurple)
                        },
                        modifier = Modifier.clickable {
                            trackToEditAlbum = song
                            showTrackMenu = null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {}
        )
    }

    // --- Selecionar playlist ---
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

    // --- Editar nome da música --
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

    // --- Editar artista da música --
    trackToEditArtist?.let { song ->
        EditTrackArtistDialog(
            song = song,
            onDismiss = { trackToEditArtist = null },
            onSave = { newArtist ->
                systemViewModel.updateTrackArtist(song, newArtist)
                trackToEditArtist = null
            }
        )
    }

    // --- Editar album da música --
    trackToEditAlbum?.let { song ->
        EditTrackAlbumDialog(
            song = song,
            onDismiss = { trackToEditAlbum = null },
            onSave = { newAlbum ->
                systemViewModel.updateTrackAlbum(song, newAlbum)
                trackToEditAlbum = null
            }
        )
    }

    // --- Confirmar exclusão de playlist --
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceDark,
            title = { Text("Excluir Playlist?", color = Color.White) },
            text = {
                Text(
                    "\"$playlistName\" será excluída permanentemente.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        systemViewModel.deletePlaylist(title)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
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
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedLabelColor = YTDownPurple
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EditTrackArtistDialog(
    song: DownloadItemEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var artist by remember { mutableStateOf(song.artist ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Editar Artista", color = Color.White) },
        text = {
            TextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artista") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedLabelColor = YTDownPurple
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(artist) },
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EditTrackAlbumDialog(
    song: DownloadItemEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var album by remember { mutableStateOf(song.album ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Editar Álbum", color = Color.White) },
        text = {
            TextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Álbum") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedLabelColor = YTDownPurple
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(album) },
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
