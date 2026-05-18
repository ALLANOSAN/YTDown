package com.example.ytdown.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.core.audio.EqualizerViewModel
import kotlin.math.sin

private val frequencies = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão de voltar
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                
                Column {
                    Text(
                        text = "Equalizador",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Personalize o som",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Spectrum Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 20.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                RealtimeSpectrum(
                    data = uiState.spectrumData,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Equalizer Bands
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                itemsIndexed(frequencies) { index, freq ->
                    ModernVerticalSlider(
                        label = freq,
                        gain = uiState.bandGains[index],
                        onGainChange = { viewModel.setBandGain(index, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Preamp Card
            ModernPreampCard(
                preamp = uiState.preamp,
                onPreampChange = { viewModel.updatePreamp(it) }
            )

            // Presets Section
            ModernPresetsSection()

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ModernVerticalSlider(label: String, gain: Float, onGainChange: (Float) -> Unit) {
    val animatedGain by animateFloatAsState(
        targetValue = gain,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gain"
    )

    // Cor baseada no ganho
    val gainColor = when {
        gain > 6f -> Color(0xFF4CAF50) // Verde para boost alto
        gain > 0f -> Color(0xFF8BC34A)   // Verde claro
        gain < -6f -> Color(0xFFE53935) // Vermelho para corte alto
        gain < 0f -> Color(0xFFFF7043)  // Laranja
        else -> YTDownPurple           // Roxo para flat
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        // Valor atual
        Text(
            text = "${animatedGain.toInt()}dB",
            color = gainColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Slider
        Box(
            modifier = Modifier
                .height(200.dp)
                .width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Track de fundo
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Barra de fundo
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.1f),
                    cornerRadius = CornerRadius(20f, 20f),
                    size = Size(size.width, size.height)
                )
                // Centro
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2f
                )
            }

            // Slider
            Slider(
                value = animatedGain,
                onValueChange = onGainChange,
                valueRange = -15f..15f,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -90f }
                    .width(200.dp),
                colors = SliderDefaults.colors(
                    thumbColor = gainColor,
                    activeTrackColor = gainColor,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Label
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ModernPreampCard(preamp: Float, onPreampChange: (Float) -> Unit) {
    val preampColor = when {
        preamp > 6f -> Color(0xFF4CAF50)
        preamp > 0f -> Color(0xFF8BC34A)
        preamp < -6f -> Color(0xFFE53935)
        preamp < 0f -> Color(0xFFFF7043)
        else -> YTDownPurple
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Preamp",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ganho global",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = "${preamp.toInt()} dB",
                    color = preampColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = preamp,
                onValueChange = onPreampChange,
                valueRange = -15f..15f,
                colors = SliderDefaults.colors(
                    thumbColor = preampColor,
                    activeTrackColor = preampColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            // Indicadores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-15", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Text("0", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Text("+15", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ModernPresetsSection() {
    val presets = listOf(
        "Flat" to 0f,
        "Rock" to listOf(4f, 3f, 0f, -1f, -2f, 0f, 2f, 3f, 4f, 3f),
        "Pop" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f),
        "Jazz" to listOf(3f, 2f, 0f, 2f, -2f, -2f, 0f, 2f, 3f, 4f),
        "Classical" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Bass Boost" to listOf(8f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 2f, 4f, 6f, 8f, 10f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.take(4).forEach { (name, _) ->
                PresetChip(name = name)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.drop(4).forEach { (name, _) ->
                PresetChip(name = name)
            }
        }
    }
}

@Composable
fun PresetChip(name: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = name,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun RealtimeSpectrum(data: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barCount = data.size.coerceAtLeast(10)
        val barWidth = (size.width - (barCount - 1) * 8f) / barCount
        val maxHeight = size.height

        // Gradiente vertical
        val gradient = Brush.verticalGradient(
            colors = listOf(
                YTDownPurple,
                Color(0xFF00D9FF),
                Color(0xFF4CAF50)
            )
        )

        data.forEachIndexed { i, value ->
            val x = i * (barWidth + 8f)
            val barHeight = (value * maxHeight).coerceIn(4f, maxHeight)

            // Barra com gradiente
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, maxHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )

            // Brilho
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(x + 2, maxHeight - barHeight + 4),
                size = Size(barWidth - 4, 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}