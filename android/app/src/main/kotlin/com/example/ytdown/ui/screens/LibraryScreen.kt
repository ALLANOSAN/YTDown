package com.example.ytdown.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.PlaylistWithCount
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.components.StaggeredVerticalEntrance
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ytdown.ui.LibraryViewModel
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext


@Composable
fun LibraryScreen(
        viewModel: DownloadViewModel,
        systemViewModel: SystemViewModel,
        playerViewModel: PlayerViewModel,
        libraryViewModel: LibraryViewModel = hiltViewModel(),
        onNavigateToPlayer: () -> Unit,
        onNavigateToDetail: (String) -> Unit,
        onNavigateToPlaylist: (String) -> Unit = {},
        modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(0) }
    val categories = listOf("Artistas", "Álbuns", "Músicas", "Playlists")
    val allItems: List<DownloadItemEntity> by viewModel.allDownloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by systemViewModel.playlists.collectAsStateWithLifecycle()
    val selectedFolders by libraryViewModel.selectedFolders.collectAsStateWithLifecycle()
    val recentSearches by libraryViewModel.recentSearches.collectAsStateWithLifecycle()
    val recentlyAdded by libraryViewModel.recentlyAdded.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            // Concede permissão persistente para que o app possa acessar e EDITAR a pasta após reiniciar
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val path = it.toString()
            libraryViewModel.addFolder(path)
        }
    }

    val completedSongs =
            remember(allItems, searchQuery) {
                allItems.filter { it.status == "completed" }.let { items ->
                    if (searchQuery.isBlank()) items
                    else
                            items.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist?.contains(searchQuery, ignoreCase = true) ==
                                                true ||
                                        it.album?.contains(searchQuery, ignoreCase = true) == true
                            }
                }
            }

    // Diálogos de edição de artista/álbum
    var editingItem by remember { mutableStateOf<EditingMetadata?>(null) }

    // Diálogos de playlist para músicas (na aba Músicas)
    var songForPlaylist by remember { mutableStateOf<DownloadItemEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibrarySearchBar(
                query = searchQuery, 
                onQueryChange = { 
                    searchQuery = it
                    libraryViewModel.onSearchQueryChanged(it)
                },
                onFocusChange = { isSearchFocused = it }
            )

            // Buscas Recentes
            if (isSearchFocused && searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
                RecentSearchesList(
                    searches = recentSearches,
                    onSearchClick = { 
                        searchQuery = it
                        libraryViewModel.onSearchQueryChanged(it)
                    },
                    onDeleteSearch = { libraryViewModel.deleteSearch(it) }
                )
            }

            // Botão para adicionar pastas de música fora do app
            if (selectedCategory == 2 && !isSearchFocused) { // Aba Músicas
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = YTDownPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar Pasta Local", fontSize = 13.sp)
                    }
                    if (selectedFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { libraryViewModel.performFullScan() }) {
                            Icon(Icons.Default.Refresh, null, tint = YTDownPurple)
                        }
                    }
                }
                
                if (selectedFolders.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Pastas selecionadas:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        selectedFolders.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    path.split("/").last(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { libraryViewModel.removeFolder(path) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (!isSearchFocused) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedCategory,
                    containerColor = Color.Black,
                    contentColor = YTDownPurple,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedCategory),
                            color = YTDownPurple
                        )
                    },
                    tabs = {
                        categories.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedCategory == index,
                                onClick = {
                                    libraryViewModel.triggerHapticSelection()
                                    selectedCategory = index
                                },
                                text = {
                                    Text(
                                        text = title,
                                        color = if (selectedCategory == index) YTDownPurple else Color.White,
                                        fontWeight = if (selectedCategory == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedCategory) {
                    // --- ABA ARTISTAS ---
                    0 ->
                            GroupedList(
                                    groups = completedSongs.groupBy { it.artist ?: "Desconhecido" },
                                    icon = Icons.Default.Person,
                                    onNavigate = onNavigateToDetail,
                                    isArtistGroup = true,
                                    libraryViewModel = libraryViewModel,
                                    onLongClick = { name, photo ->
                                        editingItem = EditingMetadata(name, photo, isArtist = true)
                                    }
                            )

                    // --- ABA ÁLBUNS ---
                    1 ->
                            GroupedList(
                                    groups = completedSongs.groupBy { it.album ?: "Sem Álbum" },
                                    icon = Icons.Default.Album,
                                    onNavigate = onNavigateToDetail,
                                    isArtistGroup = false,
                                    libraryViewModel = libraryViewModel,
                                    onLongClick = { name, photo ->
                                        editingItem = EditingMetadata(name, photo, isArtist = false)
                                    }
                            )

                    // --- ABA MÚSICAS ---
                    2 ->
                            SongsList(
                                    songs = completedSongs,
                                    recentlyAdded = recentlyAdded,
                                    playerViewModel = playerViewModel,
                                    libraryViewModel = libraryViewModel,
                                    onNavigateToPlayer = onNavigateToPlayer,
                                    onAddToPlaylist = { song -> songForPlaylist = song }
                            )

                    // --- ABA PLAYLISTS ---
                    3 ->
                            PlaylistsTab(
                                    playlists = playlists,
                                    libraryViewModel = libraryViewModel,
                                    onNavigateToDetail = onNavigateToPlaylist,
                                    onShowCreateDialog = { showCreatePlaylistDialog = true }
                            )
                }
            } else if (searchQuery.isNotEmpty()) {
                // Resultados de busca em tempo real quando focado
                SongsList(
                    songs = completedSongs,
                    recentlyAdded = emptyList(),
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onAddToPlaylist = { song -> songForPlaylist = song }
                )
            }
        }
    }

    // --- Dialogos ---
    editingItem?.let { item ->
        EditLibraryDialog(
                item = item,
                onDismiss = { editingItem = null },
                onSave = { newName, newPhoto, isArtist ->
                    if (isArtist) {
                        systemViewModel.updateArtistBatch(item.name, newName, newPhoto)
                    } else {
                        systemViewModel.updateAlbumBatch(
                                oldAlbum = item.name,
                                newAlbum = newName,
                                photo = newPhoto
                        )
                    }
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

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTES DE BUSCA RECENTE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentSearchesList(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "Buscas Recentes",
            color = YTDownPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        searches.take(5).forEach { search ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick(search) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(search, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDeleteSearch(search) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ABA MÚSICAS
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsList(
        songs: List<DownloadItemEntity>,
        recentlyAdded: List<DownloadItemEntity>,
        playerViewModel: PlayerViewModel,
        libraryViewModel: LibraryViewModel,
        onNavigateToPlayer: () -> Unit,
        onAddToPlaylist: (DownloadItemEntity) -> Unit
) {
    var songMenu by remember { mutableStateOf<DownloadItemEntity?>(null) }

    if (songs.isEmpty()) {
        EmptyLibraryMessage()
        return
    }

    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- SMART PLAYLIST: RECENTLY ADDED ---
        if (recentlyAdded.isNotEmpty() && songs.size > 10) {
            item {
                Text(
                    "Adicionadas Recentemente",
                    color = YTDownPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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
                Text(
                    "Todas as Músicas",
                    color = YTDownPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        itemsIndexed(items = songs, key = { _, s -> s.id }) { index, song ->
            StaggeredVerticalEntrance(index = index) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
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
                ) { DownloadItemRow(item = song, onClick = {}) }
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
                        ListItem(
                                headlineContent = { Text("Adicionar à Playlist") },
                                leadingContent = {
                                    Icon(
                                            Icons.AutoMirrored.Filled.PlaylistAdd,
                                            null,
                                            tint = YTDownPurple
                                    )
                                },
                                modifier =
                                        Modifier.clickable {
                                            onAddToPlaylist(song)
                                            songMenu = null
                                        },
                                colors =
                                        ListItemDefaults.colors(
                                                containerColor = Color.Transparent,
                                                headlineColor = Color.White
                                        )
                        )
                    }
                },
                confirmButton = {}
        )
    }
}

@Composable
private fun RecentSongCard(song: DownloadItemEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = song.thumbnailPath,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ABA PLAYLISTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistsTab(
        playlists: List<PlaylistWithCount>,
        libraryViewModel: LibraryViewModel,
        onNavigateToDetail: (String) -> Unit,
        onShowCreateDialog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
                onClick = onShowCreateDialog,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple),
                shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nova Playlist", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            EmptyLibraryMessage()
            return@Column
        }

        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items = playlists, key = { _, it -> it.playlist.id }) { index, playlistWithCount ->
                StaggeredVerticalEntrance(index = index) {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceDark)
                                            .clickable {
                                                libraryViewModel.triggerHapticClick()
                                                onNavigateToDetail(playlistWithCount.playlist.id)
                                            }
                                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!playlistWithCount.playlist.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                    model = playlistWithCount.playlist.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                    modifier = Modifier.size(60.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1A1A1A)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            null,
                                            tint = YTDownPurple
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    playlistWithCount.playlist.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                            )
                            Text(
                                    "${playlistWithCount.trackCount} músicas",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                            )
                        }

                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GRUPOS (Artistas / Álbuns)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedList(
        groups: Map<String, List<DownloadItemEntity>>,
        icon: ImageVector,
        onNavigate: (String) -> Unit,
        libraryViewModel: LibraryViewModel,
        isArtistGroup: Boolean = false,
        onLongClick: ((String, String?) -> Unit)? = null
) {
    if (groups.isEmpty()) {
        EmptyLibraryMessage()
        return
    }
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val keys = groups.keys.toList()
        itemsIndexed(items = keys, key = { _, it -> it }) { index, key ->
            StaggeredVerticalEntrance(index = index) {
                val groupItems = groups[key] ?: emptyList()
                var artwork =
                        groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl
                if (isArtistGroup) {
                    artwork =
                            groupItems
                                    .firstOrNull { !it.artistImageUrl.isNullOrEmpty() }
                                    ?.artistImageUrl
                }
                if (artwork.isNullOrEmpty()) {
                    artwork =
                            groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath
                }

                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                                onClick = {
                                                    libraryViewModel.triggerHapticClick()
                                                    onNavigate(key)
                                                },
                                                onLongClick = {
                                                    libraryViewModel.triggerHapticHeavy()
                                                    onLongClick?.invoke(key, artwork)
                                                }
                                        ),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artwork != null) {
                        AsyncImage(
                                model = artwork,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                                modifier = Modifier.size(60.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1A1A1A)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${groupItems.size} músicas", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIÁLOGOS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EditLibraryDialog(
        item: EditingMetadata,
        onDismiss: () -> Unit,
        onSave: (newName: String, newPhoto: String?, isArtist: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                selectedImageUri = uri
            }

    val itemTypeLabel = if (item.isArtist) "Artista" else "Álbum"

    AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark,
            title = { Text("Editar $itemTypeLabel", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                            modifier =
                                    Modifier.size(120.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.Black)
                                            .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        val previewImage = selectedImageUri ?: item.currentPhoto
                        if (previewImage != null) {
                            AsyncImage(
                                    model = previewImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, null, tint = YTDownPurple)
                        }
                    }
                    Text(
                            "Toque para mudar a foto",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome") },
                            colors =
                                    TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                    )
                    )
                }
            },
            confirmButton = {
                Button(
                        onClick = { onSave(name, selectedImageUri?.toString(), item.isArtist) },
                        colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) { Text("Salvar em Lote") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
            }
    )
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark,
            title = { Text("Nova Playlist", color = Color.White) },
            text = {
                TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome da playlist") },
                        singleLine = true,
                        colors =
                                TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedLabelColor = YTDownPurple
                                )
                )
            },
            confirmButton = {
                Button(
                        onClick = { if (name.isNotBlank()) onCreate(name) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
            }
    )
}

data class EditingMetadata(val name: String, val currentPhoto: String?, val isArtist: Boolean)

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTES COMUNS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
    query: String = "", 
    onQueryChange: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp)),
            color = Color(0xFF1A1A1A)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle =
                            androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp
                            ),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Buscar na biblioteca...", color = TextSecondary)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
                text = "Nenhum item encontrado.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge
        )
    }
}