package com.example.ytdown.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.core.domain.DownloadItemEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person

@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel,
    systemViewModel: SystemViewModel,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val completedSongs = uiState.songs
    val recentlyAdded by libraryViewModel.recentlyAdded.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by systemViewModel.playlists.collectAsStateWithLifecycle()
    val recentSearches by libraryViewModel.recentSearches.collectAsStateWithLifecycle()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    
    var editingItem by remember { mutableStateOf<EditingMetadata?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylist by remember { mutableStateOf<DownloadItemEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LibrarySearchBar(
            query = searchQuery,
            onQueryChange = { 
                searchQuery = it
                libraryViewModel.onSearchQueryChanged(it)
            },
            onFocusChange = { isSearchFocused = it }
        )

        if (!isSearchFocused && searchQuery.isEmpty()) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Black) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Artistas", modifier = Modifier.padding(16.dp), color = Color.White) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Álbuns", modifier = Modifier.padding(16.dp), color = Color.White) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Músicas", modifier = Modifier.padding(16.dp), color = Color.White) }
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) { Text("Playlists", modifier = Modifier.padding(16.dp), color = Color.White) }
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
                2 -> SongsList(
                    songs = completedSongs,
                    recentlyAdded = recentlyAdded,
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onAddToPlaylist = { song -> songForPlaylist = song },
                    onEditName = { song -> systemViewModel.updateTrackName(song, song.title) },
                    onSuperFix = { song -> systemViewModel.superFixID3(song) }
                )
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
                playerViewModel = playerViewModel,
                libraryViewModel = libraryViewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onAddToPlaylist = { song -> songForPlaylist = song },
                onEditName = { song -> systemViewModel.updateTrackName(song, song.title) },
                onSuperFix = { song -> systemViewModel.superFixID3(song) }
            )
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
