package com.example.ytdown.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel,
    systemViewModel: SystemViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(0) }
    val categories = listOf("Músicas", "Álbuns", "Artistas", "Pastas")
    val allItems by viewModel.downloads.collectAsState()
    val completedSongs = allItems.filter { it.status == "completed" }

    // Estado para o Diálogo de Edição
    var editingItem by remember { mutableStateOf<EditingMetadata?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibrarySearchBar()

            ScrollableTabRow(
                selectedTabIndex = selectedCategory,
                containerColor = Color.Black,
                contentColor = YTDownPurple,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedCategory]),
                        color = YTDownPurple
                    )
                },
                divider = {}
            ) {
                categories.forEachIndexed { index, title ->
                    var labelColor = Color.White
                    if (selectedCategory == index) {
                        labelColor = YTDownPurple
                    }
                    var labelFontWeight = FontWeight.Normal
                    if (selectedCategory == index) {
                        labelFontWeight = FontWeight.Bold
                    }

                    Tab(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        text = {
                            Text(
                                text = title,
                                color = labelColor,
                                fontWeight = labelFontWeight
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedCategory) {
                0 -> SongsList(completedSongs, playerViewModel, onNavigateToPlayer)
                1 -> GroupedList(
                    completedSongs.groupBy { it.album },
                    Icons.Default.MusicNote,
                    onNavigateToDetail,
                    isArtistGroup = false,
                    onLongClick = { album, photo -> editingItem = EditingMetadata(album, photo, isArtist = false) }
                )
                2 -> GroupedList(
                    completedSongs.groupBy { it.artist },
                    Icons.Default.Person,
                    onNavigateToDetail,
                    isArtistGroup = true,
                    onLongClick = { artist, photo -> editingItem = EditingMetadata(artist, photo, isArtist = true) }
                )
                3 -> GroupedList(completedSongs.groupBy { it.folderName }, Icons.Default.Folder, onNavigateToDetail)
            }
        }
    }

    // Diálogo de Edição (Se ativo)
    editingItem?.let { item ->
        EditLibraryDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { newName, newPhoto, isArtist ->
                if (isArtist) {
                    systemViewModel.updateArtistBatch(item.name, newName, newPhoto)
                }
                if (!isArtist) {
                    systemViewModel.updateAlbumBatch(oldAlbum = item.name, newAlbum = newName, photo = newPhoto)
                }
                editingItem = null
            }
        )
    }
}

data class EditingMetadata(val name: String, val currentPhoto: String?, val isArtist: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedList(
    groups: Map<String?, List<com.example.ytdown.core.domain.DownloadItemEntity>>,
    icon: ImageVector,
    onNavigate: (String) -> Unit,
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
            items(groups.keys.toList().filterNotNull()) { key ->
                val groupItems = groups[key] ?: emptyList()
                var artwork = groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl
                if (isArtistGroup) {
                    artwork = groupItems.firstOrNull { !it.artistImageUrl.isNullOrEmpty() }?.artistImageUrl
                }
                if (artwork.isNullOrEmpty()) {
                    artwork = groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onNavigate(key) },
                            onLongClick = { onLongClick?.invoke(key, artwork) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artwork != null) {
                        AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (artwork.isNullOrEmpty()) {
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

@Composable
fun EditLibraryDialog(
    item: EditingMetadata,
    onDismiss: () -> Unit,
    onSave: (newName: String, newPhoto: String?, isArtist: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    var itemTypeLabel = "Álbum"
    if (item.isArtist) {
        itemTypeLabel = "Artista"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Editar $itemTypeLabel", color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Foto de Preview
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val previewImage = selectedImageUri ?: item.currentPhoto
                    if (previewImage != null) {
                        AsyncImage(model = previewImage, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                    if (previewImage == null) {
                        Icon(Icons.Default.AddAPhoto, null, tint = YTDownPurple)
                    }
                }
                Text("Toque para mudar a foto", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedImageUri?.toString(), item.isArtist) },
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) {
                Text("Salvar em Lote")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
        }
    )
}

@Composable
private fun SongsList(songs: List<com.example.ytdown.core.domain.DownloadItemEntity>, playerViewModel: PlayerViewModel, onNavigateToPlayer: () -> Unit) {
    if (songs.isEmpty()) {
        EmptyLibraryMessage()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs) { song ->
                DownloadItemRow(
                    item = song,
                    onClick = {
                        playerViewModel.playTrack(song)
                        onNavigateToPlayer()
                    }
                )
            }
        }
    }
}

@Composable
private fun LibrarySearchBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp)),
        color = Color(0xFF1A1A1A)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Buscar na biblioteca...",
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Nenhum item encontrado.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}