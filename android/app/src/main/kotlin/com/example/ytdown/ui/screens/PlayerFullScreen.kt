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

    // Alterna entre albumArtwork e artistArtwork persistido no SongEntity
    val currentArtwork = if (showArtistArt && !track.artistImageUrl.isNullOrBlank()) {
        track.artistImageUrl // Cache do Fanart.tv
    } else {
        track.albumImageUrl ?: track.thumbnailPath // Capa do arquivo ou CAA
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
                    .padding(top = 12.dp),
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

            // ARTWORK
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(artworkSize)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = currentArtwork,
                    label = "ArtworkTransition"
                ) { image ->
                    if (!image.isNullOrEmpty()) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(28.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
            }

            // SPECTRUM VISUALIZER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(top = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                SpectrumVisualizer(
                    spectrumData = uiState.spectrumData,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .alpha(0.8f),
                    barColor = YTDownPurple
                )
            }

            // TEXTOS (Título e Artista)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = track.artist ?: "Desconhecido",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // SLIDER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
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

            // ESPAÇADOR ANTES DOS CONTROLES
            Spacer(modifier = Modifier.height(24.dp))

            // PASSO 6 — IMPLEMENTAR CONTROLES CORRETAMENTE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 40.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        viewModel.playPrevious()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.rewind()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier.size(82.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {

                    Icon(
                        imageVector =
                            if (isPlaying)
                                Icons.Default.Pause
                            else
                                Icons.Default.PlayArrow,

                        contentDescription = "PlayPause",

                        tint = Color.White,

                        modifier = Modifier.size(42.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.forward()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.playNext()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // ESPAÇADOR FINAL
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
