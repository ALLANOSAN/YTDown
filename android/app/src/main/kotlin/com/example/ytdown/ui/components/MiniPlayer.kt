package com.example.ytdown.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import coil.request.CachePolicy
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.TextSecondary

@Composable
fun MiniPlayer(
    viewModel: PlaybackViewModel,
    onClick: () -> Unit
) {
    val uiState by viewModel.playbackUiState.collectAsStateWithLifecycle()

    val track = uiState.currentTrack
    val isPlaying = uiState.isPlaying
    val position = uiState.currentPositionMs
    val duration = uiState.durationMs

    // ROTAÇÃO PROFISSIONAL: Album Art <-> Artist Art
    var showArtistArt by remember { mutableStateOf(false) }
    
    // Reinicia o ciclo sempre que a track muda
    LaunchedEffect(track?.id) {
        showArtistArt = false // Começa sempre pelo álbum
        while (true) {
            delay(10000)
            if (!track?.artistArtPath.isNullOrBlank()) {
                showArtistArt = !showArtistArt
            }
        }
    }

    val currentArtwork = remember(showArtistArt, track) {
        if (showArtistArt && !track?.artistArtPath.isNullOrEmpty()) 
            track.artistArtPath 
        else 
            track?.albumArtPath ?: track?.albumArtPath
    }

    if (track == null) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() }
            .zIndex(3f)
            .alpha(1f),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E1E),
                            Color(0xFF0A0A0A)
                        )
                    )
                )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Crossfade(
                        targetState = currentArtwork,
                        animationSpec = tween(500),
                        label = "MiniPlayerArtwork"
                    ) { artwork ->
                        if (!artwork.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artwork)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .allowHardware(false) // Necessário para animações pesadas
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(YTDownPurple, Color.DarkGray)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = track.artist ?: "Desconhecido",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.playPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.playNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Próxima", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                if (duration > 0) {
                    val progress = remember(position, duration) {
                        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(YTDownPurple)
                        )
                    }
                }
            }
        }
    }
}