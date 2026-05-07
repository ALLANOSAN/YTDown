package com.example.ytdown.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.media3.common.Player
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
import com.example.ytdown.ui.theme.SurfaceDark
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
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val dominantColorInt by viewModel.dominantColor.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val accentColor = remember(dominantColorInt) {
        dominantColorInt?.let { Color(it) } ?: YTDownPurple
    }

    val currentTrack = track
    if (currentTrack == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Nenhuma música carregada", color = Color.White)
        }
        return
    }

    val artworkSource = remember(currentTrack, showArtistImage) {
        var source = currentTrack.thumbnailPath
        if (showArtistImage && !currentTrack.artistImageUrl.isNullOrEmpty()) {
            source = currentTrack.artistImageUrl
        }
        if (source.isNullOrEmpty() && !currentTrack.albumImageUrl.isNullOrEmpty()) {
            source = currentTrack.albumImageUrl
        }
        source
    }

    // --- AURORA UI ANIMATION ---
    val infiniteTransition = rememberInfiniteTransition(label = "Aurora")
    val auroraOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "A1"
    )
    val auroraOffset2 by infiniteTransition.animateFloat(
        initialValue = 1000f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "A2"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Background Blur Animado
        AnimatedContent(
            targetState = artworkSource,
            transitionSpec = {
                fadeIn(animationSpec = tween(1200)) togetherWith fadeOut(animationSpec = tween(1200))
            },
            label = "BackgroundBlur"
        ) { targetSource ->
            AsyncImage(
                model = targetSource,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(60.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
        }

        // 2. Aurora Mesh Gradient Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(auroraOffset1, auroraOffset2),
                        radius = 800f
                    )
                )
        )

        // 3. Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
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
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "TOCANDO AGORA",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (sleepTimerMinutes != null) {
                        Text(
                            "Timer: ${sleepTimerMinutes}m",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(
                        if (sleepTimerMinutes != null) Icons.Default.Timer else Icons.Outlined.Timer,
                        null,
                        tint = if (sleepTimerMinutes != null) accentColor else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))

            // Album Art
            AnimatedContent(
                targetState = artworkSource,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(800)) + scaleIn(initialScale = 0.85f)) togetherWith
                    (fadeOut(animationSpec = tween(800)) + scaleOut(targetScale = 0.85f))
                },
                label = "AlbumArt"
            ) { targetSource ->
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(32.dp)),
                    tonalElevation = 12.dp,
                    shadowElevation = 20.dp,
                    color = Color.DarkGray
                ) {
                    AsyncImage(
                        model = targetSource,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))

            // Track Info
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = track?.title ?: "Título Desconhecido",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track?.artist ?: "Desconhecido",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Bar
            Column {
                Slider(
                    value = position.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accentColor,
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

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shuffleTint = if (isShuffleEnabled) accentColor else Color.White.copy(alpha = 0.5f)
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, null, tint = shuffleTint, modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                
                Surface(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }

                val repeatTint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else Color.White.copy(alpha = 0.5f)
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        null,
                        tint = repeatTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSelect = { 
                viewModel.setSleepTimer(it)
                showSleepTimerDialog = false
            },
            currentTimer = sleepTimerMinutes
        )
    }
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
    currentTimer: Int?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Sleep Timer", color = Color.White) },
        text = {
            Column {
                listOf(null, 15, 30, 45, 60).forEach { minutes ->
                    val label = if (minutes == null) "Desativado" else "$minutes minutos"
                    val isSelected = currentTimer == minutes
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(minutes) },
                            colors = RadioButtonDefaults.colors(selectedColor = YTDownPurple)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, color = if (isSelected) Color.White else TextSecondary)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
