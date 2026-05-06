@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.example.ytdown.core.domain.VideoUrl
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.components.DownloadOptionsBottomSheet
import com.example.ytdown.ui.components.ShimmerItem
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.utils.YouTubeUtils

@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    onNavigateToPlaylistSelection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val state by viewModel.inputState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    val onSearchAction = {
        val extracted = YouTubeUtils.extractUrl(state.urlInput) ?: state.urlInput
        if (extracted.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.fetchVideoDetails(context, VideoUrl(extracted))
        }
    }

    // Navegar automaticamente para seleção de playlist se detectado
    LaunchedEffect(state.isPlaylist, state.showDialog, state.fetchedItems) {
        if (state.isPlaylist && state.showDialog && state.fetchedItems.isNotEmpty()) {
            keyboardController?.hide()
            onNavigateToPlaylistSelection()
        }
    }

    // Exibir Opções de Download em BottomSheet
    if (state.showDialog && !state.isPlaylist) {
        SideEffect {
            keyboardController?.hide()
        }
        DownloadOptionsBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.onDismissDialog() }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(YTDownPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "YTDown",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Baixe vídeos do YouTube",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configurações",
                        tint = Color.White
                    )
                }
            }

            Text(
                text = "Cole o link do YouTube",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = state.urlInput,
                    onValueChange = viewModel::onUrlInputChanged,
                    placeholder = { Text("https://youtube.com...", color = TextSecondary) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (state.urlInput.isEmpty()) {
                            IconButton(onClick = {
                                scope.launch {
                                    val clipEntry = clipboard.getClipEntry()
                                    val clipText = clipEntry?.clipData?.getItemAt(0)?.text?.toString()

                                    if (!clipText.isNullOrBlank()) {
                                        viewModel.onUrlInputChanged(clipText)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "Colar",
                                    tint = TextSecondary
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.onUrlInputChanged("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Limpar",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchAction() }
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onSearchAction,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(YTDownPurple)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (state.isFetching) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerItem()
                    ShimmerItem()
                }
            }

            // Exibe erro de fetch (URL inválida, sem internet, etc.)
            if (!state.fetchError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF3D1A1A)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val errorText = state.fetchError ?: ""
                        Text(
                            text = errorText,
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearFetchError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (recentSearches.isNotEmpty()) {
                // Recent Searches Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = YTDownPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Buscas Recentes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentSearches) { query ->
                        RecentSearchItem(
                            text = query,
                            onClick = {
                                viewModel.onUrlInputChanged(query)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchAction()
                            },
                            onDelete = {
                                viewModel.deleteRecentSearch(query)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchItem(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remover",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}