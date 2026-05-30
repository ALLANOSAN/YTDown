package com.example.ytdown.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.components.SpectrumVisualizer
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.core.artwork.ArtworkMode

@Composable
fun PlayerFullScreen(
    viewModel: PlaybackViewModel,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {}
) {
    val uiState by viewModel.playbackUiState.collectAsStateWithLifecycle()

    val track = uiState.currentTrack
    val isPlaying = uiState.isPlaying
    val position = uiState.currentPositionMs
    val duration = uiState.durationMs
    val isShuffleEnabled = uiState.isShuffleEnabled
    val repeatMode = uiState.repeatMode

    // SISTEMA DE ALTERNÂNCIA DE ARTWORK (Passo 15)
    var showArtistArt by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                kotlinx.coroutines.delay(10000)
                showArtistArt = !showArtistArt
            }
        }
    }

    var sliderPosition by remember(position) { mutableStateOf(position.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // CÁLCULO DE TAMANHO RESPONSIVO PARA ARTWORK
    val configuration = LocalConfiguration.current
    val artworkSize = (configuration.screenWidthDp.dp * 0.82f).coerceAtMost(380.dp)

    if (track == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("Nenhuma música carregada", color = Color.White)
        }
        return
    }

    // Alterna entre albumArtPath e artistArtPath
    val currentArtwork = if (showArtistArt && !track.artistArtPath.isNullOrBlank()) {
        track.artistArtPath // Cache do Fanart.tv
    } else {
        track.albumArtPath // Capa do arquivo ou CAA
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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

                IconButton(onClick = onNavigateToEqualizer) {
                    Icon(Icons.Default.Equalizer, "Equalizador", tint = Color.White)
                }
            }

            // ARTWORK (Responsivo com weight(1f) para preencher o espaço disponível sem quebrar o layout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.DarkGray)
                ) {
                    Crossfade(
                        targetState = currentArtwork,
                        label = "ArtworkTransition"
                    ) { image ->
                        if (!image.isNullOrEmpty()) {
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop, // Preenche a área 1:1 mantendo a proporção (sem esticar)
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxSize(0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // SPECTRUM VISUALIZER (Mais compacto para evitar empurrar os controles)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SpectrumVisualizer(
                    spectrumData = uiState.spectrumData,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .alpha(0.8f),
                    barColor = YTDownPurple
                )
            }

            // TEXTOS (Título e Artista)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    maxLines = 1, // Reduzido para 1 linha para economizar espaço vertical em telas pequenas
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = track.artist ?: "Desconhecido",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // SLIDER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Slider(
                    value = if (isDragging) sliderPosition else position.toFloat(),
                    onValueChange = { 
                        isDragging = true
                        sliderPosition = it 
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        viewModel.seekTo(sliderPosition.toLong())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = YTDownPurple,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(if (isDragging) sliderPosition.toLong() else position), color = Color.Gray, fontSize = 12.sp)
                    Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
                }
            }

            // CONTROLES (Posicionados com segurança na parte inferior)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp)
                    .zIndex(10f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Linha principal: Previous | Rewind | Play/Pause | Forward | Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.rewind() }) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Retroceder 10s",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.forward() }) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Avançar 10s",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Próxima",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Linha secundária: Shuffle | Repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Embaralhar",
                            tint = if (isShuffleEnabled) YTDownPurple else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Repeat (3 estados: OFF → ALL → ONE → OFF)
                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            imageVector = when (repeatMode) {
                                2 -> Icons.Default.RepeatOne
                                1 -> Icons.Default.Repeat
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = when (repeatMode) {
                                2 -> "Repetir uma música"
                                1 -> "Repetir todas"
                                else -> "Repetir desligado"
                            },
                            tint = when (repeatMode) {
                                0 -> Color.White.copy(alpha = 0.5f)
                                else -> YTDownPurple
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
