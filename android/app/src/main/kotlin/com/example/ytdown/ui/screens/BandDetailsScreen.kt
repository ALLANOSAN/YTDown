package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ytdown.core.infrastructure.DynamicAlbum
import com.example.ytdown.ui.BandDetailsViewModel
import com.example.ytdown.ui.theme.*

/**
 * Tela de detalhes da banda - exibe informações, discografia e opções de download
 * Usa dados da API do MusicBrainz com capas reais do Cover Art Archive
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandDetailsScreen(
    onBack: () -> Unit,
    viewModel: BandDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bandInfo = state.bandInfo

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header com imagem de fundo
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Imagem de fundo (primeira capa de álbum como background)
                val firstAlbumWithCover = bandInfo?.albums?.firstOrNull { it.coverUrl != null }
                AsyncImage(
                    model = firstAlbumWithCover?.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.95f),
                                    Color.Black
                                )
                            )
                        )
                )

                // Botão de voltar
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                // Conteúdo do header
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(top = 80.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Nome da banda
                    Text(
                        text = state.bandName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // País e status
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandInfo?.country?.let { country ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = country,
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        if (bandInfo?.isActive == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Green.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Ativa",
                                    color = Color.Green,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Contagem de álbuns
                    Text(
                        text = "${bandInfo?.albums?.size ?: 0} álbuns encontrados",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Botões de ação
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.downloadBestAlbum() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YTDownPurple
                    ),
                    enabled = !state.isLoading && state.downloadingAlbums.isEmpty()
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Baixar Melhor")
                }

                if ((bandInfo?.albums?.size ?: 0) > 1) {
                    OutlinedButton(
                        onClick = { viewModel.downloadAllAlbums() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = YTDownPurple
                        ),
                        enabled = !state.isLoading && state.downloadingAlbums.isEmpty()
                    ) {
                        Icon(
                            Icons.Default.Album,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Baixar Todos")
                    }
                }
            }
        }

        // Tags e gêneros
        val allTags = (bandInfo?.tags ?: emptyList()) + (bandInfo?.genres ?: emptyList())
        if (allTags.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allTags.distinct().take(8).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = YTDownPurple.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = tag.replaceFirstChar { it.uppercase() },
                                color = YTDownPurple,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bandas similares
        val similarArtists = bandInfo?.similarArtists ?: emptyList()
        if (similarArtists.isNotEmpty()) {
            item {
                Text(
                    text = "Bandas Similares",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(similarArtists) { artist ->
                        SimilarBandChip(bandName = artist.name)
                    }
                }
            }
        }

        // Loading state
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YTDownPurple)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Carregando discografia do MusicBrainz...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Buscando capas no Cover Art Archive",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Lista de álbuns com capas reais
        val albums = bandInfo?.albums ?: emptyList()
        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Discografia",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(albums) { album ->
                AlbumCardWithCover(
                    album = album,
                    isDownloading = album.title in state.downloadingAlbums,
                    isDownloaded = album.title in state.downloadedAlbums,
                    onDownload = { viewModel.downloadAlbumDirect(album.title, album.year) }
                )
            }
        }

        // Error state
        state.error?.let { error ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(SurfaceDark, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }

        // Espaçamento final
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimilarBandChip(bandName: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark
    ) {
        Text(
            text = bandName,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AlbumCardWithCover(
    album: DynamicAlbum,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa do álbum com imagem real ou placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(YTDownPurple.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUrl != null) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = "Capa de ${album.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = YTDownPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info do álbum
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = album.year.ifEmpty { "N/A" },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (album.type.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YTDownPurple.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = album.type,
                                color = YTDownPurple,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Botão de download
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = YTDownPurple,
                        strokeWidth = 2.dp
                    )
                }
                isDownloaded -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Baixado",
                        tint = Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Baixar",
                            tint = YTDownPurple
                        )
                    }
                }
            }
        }
    }
}