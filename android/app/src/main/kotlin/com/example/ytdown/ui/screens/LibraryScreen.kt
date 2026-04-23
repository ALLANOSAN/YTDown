package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(0) }
    val categories = listOf("Músicas", "Álbuns", "Artistas", "Pastas")
    val allItems by viewModel.downloads.collectAsState()
    val completedSongs = allItems.filter { it.status == "completed" }

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
                    Tab(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
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

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedCategory) {
                0 -> SongsList(completedSongs, playerViewModel, onNavigateToPlayer)
                1 -> GroupedList(completedSongs.groupBy { it.album }, Icons.Default.MusicNote, onNavigateToDetail)
                2 -> GroupedList(completedSongs.groupBy { it.artist }, Icons.Default.Person, onNavigateToDetail)
                3 -> GroupedList(completedSongs.groupBy { it.folderName }, Icons.Default.Folder, onNavigateToDetail)
            }
        }
    }
}

@Composable
private fun SongsList(songs: List<com.example.ytdown.core.domain.DownloadItemEntity>, playerViewModel: PlayerViewModel, onNavigateToPlayer: () -> Unit) {
    if (songs.isEmpty()) {
        EmptyLibraryMessage()
    } else {
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
private fun GroupedList(
    groups: Map<String?, List<com.example.ytdown.core.domain.DownloadItemEntity>>,
    icon: ImageVector,
    onNavigate: (String) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyLibraryMessage()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups.keys.toList().filterNotNull()) { key ->
                val groupItems = groups[key] ?: emptyList()
                val artwork = groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl 
                    ?: groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(key) },
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