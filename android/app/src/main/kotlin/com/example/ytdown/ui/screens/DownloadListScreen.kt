@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.PagingData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.*
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.components.DownloadItemRow
import com.example.ytdown.ui.components.ShimmerItem
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
        viewModel: DownloadViewModel,
        playbackViewModel: PlaybackViewModel,
        onNavigateToBrowser: () -> Unit,
        onNavigateToPlayer: () -> Unit,
        modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val downloads = viewModel.downloads
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val failedDownloads by viewModel.failedDownloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemViewModel: SystemViewModel = hiltViewModel()
    var editingDownload by remember { mutableStateOf<DownloadItemEntity?>(null) }

    Scaffold(
            modifier = modifier,
            topBar = {
                Column(modifier = Modifier.background(Color.Black)) {
                    // Barra de Busca
                    TopAppBar(
                            title = {
                                TextField(
                                        value = listState.searchQuery,
                                        onValueChange = viewModel::onSearchQueryChanged,
                                        placeholder = {
                                            Text("Buscar nos downloads...", color = TextSecondary)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                                        textStyle =
                                                LocalTextStyle.current.copy(color = Color.White),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        cursorColor = Color.White,
                                                        focusedLabelColor = Color.White,
                                                        unfocusedLabelColor = Color.White
                                                ),
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, null, tint = TextSecondary)
                                        }
                                )
                            },
                            colors =
                                    TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                            actions = {
                                if (!listState.isSelectionMode && failedDownloads.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                            viewModel.retryAllFailed()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            "Tentar novamente todos os downloads que falharam",
                                            tint = YTDownPurple
                                        )
                                    }
                                }
                                if (listState.isSelectionMode) {
                                    IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                                viewModel.selectAllDownloads()
                                            }
                                    ) {
                                        Icon(
                                                Icons.Default.Checklist,
                                                "Selecionar Tudo",
                                                tint = Color.White
                                        )
                                    }
                                    IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                                viewModel.deleteSelectedDownloads()
                                            }
                                    ) {
                                        Icon(
                                                Icons.Default.Delete,
                                                "Excluir Selecionados",
                                                tint = Color.Red
                                        )
                                    }
                                    IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove
                                                )
                                                viewModel.clearSelectionMode()
                                            }
                                    ) {
                                        Icon(
                                                Icons.Default.Close,
                                                "Sair da Seleção",
                                                tint = Color.White
                                        )
                                    }
                                }
                            }
                    )

                    // Abas de Filtro
                    PrimaryTabRow(
                            selectedTabIndex = listState.selectedTab,
                            containerColor = Color.Black,
                            contentColor = YTDownPurple,
                            indicator = {
                                TabRowDefaults.PrimaryIndicator(
                                        modifier =
                                                Modifier.tabIndicatorOffset(
                                                        selectedTabIndex = listState.selectedTab
                                                ),
                                        color = YTDownPurple
                                )
                            }
                    ) {
                        val tabs = listOf("Todos", "Áudios", "Vídeos")
                        tabs.forEachIndexed { index, title ->
                            var itemColor = TextSecondary
                            if (listState.selectedTab == index) {
                                itemColor = Color.White
                            }

                            var itemFontWeight = FontWeight.Normal
                            if (listState.selectedTab == index) {
                                itemFontWeight = FontWeight.Bold
                            }

                            Tab(
                                    selected = listState.selectedTab == index,
                                    onClick = {
                                        haptic.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                        )
                                        viewModel.onTabSelected(index)
                                    },
                                    text = {
                                        Text(
                                                text = title,
                                                color = itemColor,
                                                fontSize = 14.sp,
                                                fontWeight = itemFontWeight
                                        )
                                    }
                            )
                        }
                    }
                }
            },
            containerColor = Color.Black,
            floatingActionButton = {
                if (!listState.isSelectionMode) {
                    FloatingActionButton(
                            onClick = onNavigateToBrowser,
                            containerColor = YTDownPurple,
                            contentColor = Color.White
                    ) { Icon(Icons.Default.Language, contentDescription = "Abrir Navegador") }
                }
            }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding).fillMaxSize(), color = Color.Black) {
            val pagingItems: LazyPagingItems<DownloadItemEntity> = downloads.collectAsLazyPagingItems()

            when {
                pagingItems.loadState.refresh is androidx.paging.LoadState.Loading &&
                        pagingItems.itemCount == 0 -> {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { repeat(6) { com.example.ytdown.ui.components.ShimmerItem() } }
                }
                pagingItems.itemCount == 0 -> {
                    EmptyState(onNavigateToBrowser)
                }
                else -> {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                                count = pagingItems.itemCount,
                                key = { index -> pagingItems.peek(index)?.id ?: index }
                        ) { index ->
                            val item = pagingItems[index] ?: return@items
                            val isSelected = listState.selectedIds.contains(item.id)
                            DownloadItemRow(
                                    item = item,
                                    progressBus = systemViewModel.progressBus,
                                    isSelected = isSelected,
                                    isSelectionMode = listState.isSelectionMode,
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleItemSelection(item.id)
                                    },
                                    onClick = {
                                        if (listState.isSelectionMode) {
                                            haptic.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                            )
                                            viewModel.toggleItemSelection(item.id)
                                        } else if (item.status == "completed") {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                            playbackViewModel.playTrack(item)
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onExport = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.exportDownload(context, item.id)
                                    },
                                    onEditMetadata = {
                                        haptic.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                        )
                                        editingDownload = item
                                    },
                                    onDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.deleteDownload(item.id)
                                    },
                                    onRetry = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.retryDownload(item)
                                    }
                            )
                        }

                        // Spinner de carregamento da próxima página
                        if (pagingItems.loadState.append is androidx.paging.LoadState.Loading) {
                            item {
                                Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = YTDownPurple,
                                            strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        editingDownload?.let { item ->
            EditDownloadMetadataDialog(
                    item = item,
                    onDismiss = { editingDownload = null },
                    onSave = { newTitle, newArtist, newAlbum ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateDownloadMetadata(item.id, newTitle, newArtist, newAlbum)
                        editingDownload = null
                    }
            )
        }
    }
}

@Composable
fun EditDownloadMetadataDialog(
        item: DownloadItemEntity,
        onDismiss: () -> Unit,
        onSave: (newTitle: String, newArtist: String?, newAlbum: String?) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var artist by remember { mutableStateOf(item.artist ?: "") }
    var album by remember { mutableStateOf(item.album ?: "") }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Editar metadados", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = YTDownPurple,
                                            unfocusedBorderColor = SurfaceDark,
                                            focusedLabelColor = YTDownPurple,
                                            unfocusedLabelColor = TextSecondary
                                    )
                    )
                    OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text("Artista") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = YTDownPurple,
                                            unfocusedBorderColor = SurfaceDark,
                                            focusedLabelColor = YTDownPurple,
                                            unfocusedLabelColor = TextSecondary
                                    )
                    )
                    OutlinedTextField(
                            value = album,
                            onValueChange = { album = it },
                            label = { Text("Álbum") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = YTDownPurple,
                                            unfocusedBorderColor = SurfaceDark,
                                            focusedLabelColor = YTDownPurple,
                                            unfocusedLabelColor = TextSecondary
                                    )
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { onSave(title, artist.ifBlank { null }, album.ifBlank { null }) }
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
            }
    )
}

@Composable
private fun EmptyState(onNavigateToBrowser: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
                Icons.Default.DownloadForOffline,
                null,
                modifier = Modifier.size(80.dp),
                tint = SurfaceDark
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Nenhum download encontrado", color = TextSecondary, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
                onClick = onNavigateToBrowser,
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple),
                shape = RoundedCornerShape(12.dp)
        ) { Text("Começar a baixar", fontWeight = FontWeight.Bold) }
    }
}
