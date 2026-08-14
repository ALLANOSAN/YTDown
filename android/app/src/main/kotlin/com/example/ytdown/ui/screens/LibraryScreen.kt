package com.example.ytdown.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.core.domain.DownloadItemEntity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel,
    systemViewModel: SystemViewModel,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val completedSongs = uiState.songs
    val recentlyAdded by libraryViewModel.recentlyAdded.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by systemViewModel.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentSearches by libraryViewModel.recentSearches.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<EditingMetadata?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylist by remember { mutableStateOf<DownloadItemEntity?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { libraryViewModel.addFolder(it.toString()) }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    containerColor = YTDownPurple,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Adicionar pasta de música")
                }
            }
        },
        containerColor = Color.Transparent,
        // Este Scaffold e aninhado: o de RootApp.kt ja consome os insets das
        // barras do sistema. Sem zerar aqui, o padding entra duas vezes, a area
        // util encolhe e a lista passa a rolar sem precisar.
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            LibrarySearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    libraryViewModel.onSearchQueryChanged(it)
                },
                onFocusChange = { isSearchFocused = it }
            )

            if (!isSearchFocused && searchQuery.isEmpty()) {
                PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Black) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Artistas", modifier = Modifier.padding(vertical = 12.dp), color = Color.White, fontSize = 12.sp) }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Álbuns", modifier = Modifier.padding(vertical = 12.dp), color = Color.White, fontSize = 12.sp) }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Músicas", modifier = Modifier.padding(vertical = 12.dp), color = Color.White, fontSize = 12.sp) }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) { Text("Playlists", modifier = Modifier.padding(vertical = 12.dp), color = Color.White, fontSize = 12.sp) }
                }

                when (selectedTab) {
                    0 -> GroupedList(
                        groups = completedSongs.groupBy { it.artist ?: "Desconhecido" },
                        icon = Icons.Default.Person,
                        onNavigate = onNavigateToDetail,
                        libraryViewModel = libraryViewModel,
                        isArtistGroup = true,
                        onLongClick = { name, photo -> editingItem = EditingMetadata(name, photo, true) }
                    )
                    1 -> GroupedList(
                        groups = completedSongs.groupBy { it.album ?: "Desconhecido" },
                        icon = Icons.Default.Album,
                        onNavigate = onNavigateToDetail,
                        libraryViewModel = libraryViewModel,
                        isArtistGroup = false,
                        onLongClick = { name, photo -> editingItem = EditingMetadata(name, photo, false) }
                    )
                    2 -> Column {
                        // Seção de Pastas Monitoradas (dentro do SongsList agora)
                        val folders by libraryViewModel.selectedFolders.collectAsStateWithLifecycle()
                        SongsList(
                            songs = completedSongs,
                            recentlyAdded = recentlyAdded,
                            playbackViewModel = playbackViewModel,
                            libraryViewModel = libraryViewModel,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onAddToPlaylist = { song -> songForPlaylist = song },
                            onEditName = { song -> systemViewModel.updateTrackName(song, song.title) },
                            onSuperFix = { systemViewModel.superFixID3() },
                            onAddFolder = { folderPickerLauncher.launch(null) },
                            folders = folders.toList(),
                            onRemoveFolder = { libraryViewModel.removeFolder(it) }
                        )
                    }
                    3 -> PlaylistsTab(
                        playlists = playlists,
                        libraryViewModel = libraryViewModel,
                        onNavigateToDetail = onNavigateToPlaylist,
                        onShowCreateDialog = { showCreatePlaylistDialog = true }
                    )
                }
            } else if (isSearchFocused && searchQuery.isEmpty()) {
                RecentSearchesList(
                    searches = recentSearches,
                    onSearchClick = {
                        searchQuery = it
                        libraryViewModel.onSearchQueryChanged(it)
                    },
                    onDeleteSearch = { libraryViewModel.deleteSearch(it) }
                )
            } else {
                SongsList(
                    songs = completedSongs,
                    recentlyAdded = emptyList(),
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onAddToPlaylist = { song -> songForPlaylist = song },
                    onEditName = { song -> systemViewModel.updateTrackName(song, song.title) },
                    onSuperFix = { song -> systemViewModel.superFixID3(song) }
                )
            }
        }
    }

    // Diálogos
    editingItem?.let { item ->
        EditLibraryDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { newName, newPhoto, isArtist ->
                if (isArtist) systemViewModel.updateArtistBatch(item.name, newName, newPhoto)
                else systemViewModel.updateAlbumBatch(oldAlbum = item.name, newAlbum = newName, photo = newPhoto)
                editingItem = null
            }
        )
    }

    songForPlaylist?.let { song ->
        PlaylistSelectionDialog(
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onSelect = { playlist ->
                systemViewModel.addTrackToPlaylist(playlist.playlist.id, song.id)
                songForPlaylist = null
            }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                systemViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistWithCount>,
    libraryViewModel: LibraryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onShowCreateDialog: () -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Nenhuma playlist ainda", color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onShowCreateDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criar Playlist")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onShowCreateDialog) {
                    Icon(Icons.Default.Add, null, tint = YTDownPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nova Playlist", color = YTDownPurple)
                }
            }
        }
        items(playlists) { pwc ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDetail(pwc.playlist.id) },
                color = SurfaceDark,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = YTDownPurple, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pwc.playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${pwc.trackCount} músicas", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
