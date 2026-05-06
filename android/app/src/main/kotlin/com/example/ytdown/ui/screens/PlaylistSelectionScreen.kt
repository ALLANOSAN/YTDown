package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.components.DownloadOptionsBottomSheet
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.inputState.collectAsState()
    val selectedCount = state.fetchedItems.count { it.isSelected }
    var showOptionsSheet by remember { mutableStateOf(false) }

    // Mostra o BottomSheet de opções (tags + formato) antes de iniciar o download
    if (showOptionsSheet) {
        DownloadOptionsBottomSheet(
            viewModel = viewModel,
            onDismiss = { showOptionsSheet = false },
            onConfirm = {
                showOptionsSheet = false
                onFinish()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Selecionar Músicas", color = Color.White, fontSize = 18.sp)
                        Text("$selectedCount selecionadas", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.onSelectAllItems() }) {
                        Text("Tudo", color = YTDownPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    tonalElevation = 8.dp
                ) {
                    Button(
                        onClick = { showOptionsSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Baixar $selectedCount itens", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        if (state.fetchedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma faixa encontrada para este link.",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.fetchedItems) { item ->
                var rowBackgroundColor = SurfaceDark
                if (item.isSelected) {
                    rowBackgroundColor = YTDownPurple.copy(alpha = 0.1f)
                }

                var itemFontWeight = FontWeight.Normal
                if (item.isSelected) {
                    itemFontWeight = FontWeight.Bold
                }

                var itemIcon = Icons.Default.RadioButtonUnchecked
                if (item.isSelected) {
                    itemIcon = Icons.Default.CheckCircle
                }

                var itemTint = TextSecondary
                if (item.isSelected) {
                    itemTint = YTDownPurple
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBackgroundColor)
                        .clickable { viewModel.onVideoSelected(item, !item.isSelected) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title.value,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            fontWeight = itemFontWeight
                        )
                    }
                    
                    Icon(
                        itemIcon,
                        contentDescription = null,
                        tint = itemTint
                    )
                }
            }
        }
    }
}
