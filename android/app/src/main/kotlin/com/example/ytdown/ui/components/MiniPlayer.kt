package com.example.ytdown.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val track = state.currentTrack
    val isPlaying = state.isPlaying
    val position = state.positionMs
    val duration = state.durationMs

    if (track == null) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        color = Color.Transparent, // Usaremos o background customizado
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
                        targetState = track,
                        animationSpec = tween(500),
                        label = "MiniPlayerArtwork"
                    ) { currentTrack ->
                        AsyncImage(
                            model = currentTrack?.albumImageUrl?.takeIf { it.isNotBlank() } ?: currentTrack?.thumbnailPath,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = track?.artist ?: "Desconhecido",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.previous() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        
                        var playIcon = Icons.Default.PlayArrow
                        if (isPlaying) {
                            playIcon = Icons.Default.Pause
                        }
                        
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                playIcon,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        IconButton(onClick = { viewModel.next() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Próxima", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                
                // Barra de progresso discreta no rodapé do mini player
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
