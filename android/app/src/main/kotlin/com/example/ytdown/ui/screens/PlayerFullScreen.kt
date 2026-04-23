package com.example.ytdown.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.example.ytdown.core.domain.DownloadItemEntity

@Composable
fun PlayerFullScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val showArtistImage by viewModel.showArtistImage.collectAsState()

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma música carregada", color = Color.White)
        }
        return
    }

    val artworkSource = remember(track, showArtistImage) {
        if (showArtistImage && !track?.artistImageUrl.isNullOrEmpty()) {
            track?.artistImageUrl
        } else if (!track?.albumImageUrl.isNullOrEmpty()) {
            track?.albumImageUrl
        } else {
            track?.thumbnailPath
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Background Blur (Sigma 40 conforme original)
        AnimatedContent(
            targetState = artworkSource,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
            },
            label = "BackgroundBlur"
        ) { targetSource ->
            AsyncImage(
                model = targetSource,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(40.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ExpandMore, "Minimizar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    "TOCANDO AGORA",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Album Art com Animação de Transição
            AnimatedContent(
                targetState = artworkSource,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.9f)) togetherWith
                    (fadeOut(animationSpec = tween(600)) + scaleOut(targetScale = 0.9f))
                },
                label = "AlbumArt"
            ) { targetSource ->
                Surface(
                    modifier = Modifier
                        .size(320.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    AsyncImage(
                        model = targetSource,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Track Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = track?.title ?: "Título Desconhecido",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${track?.artist ?: "Desconhecido"} • ${track?.album ?: "Sem álbum"}",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Progress Bar
            Column {
                Slider(
                    value = position.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(position), color = TextSecondary, fontSize = 12.sp)
                    Text(formatTime(duration), color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                
                Surface(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Repeat, null, tint = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
