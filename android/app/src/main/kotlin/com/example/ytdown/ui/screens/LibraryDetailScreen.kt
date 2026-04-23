package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.theme.TextSecondary

@Composable
fun LibraryDetailScreen(
    title: String,
    viewModel: DownloadViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onBack: () -> Unit
) {
    val allItems by viewModel.downloads.collectAsState()
    val groupItems = allItems.filter { 
        it.album == title || it.artist == title || it.folderName == title 
    }.filter { it.status == "completed" }
    
    val artwork = groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl 
        ?: groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groupItems) { song ->
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
}
