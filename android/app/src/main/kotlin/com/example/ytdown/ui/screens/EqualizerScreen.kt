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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ytdown.core.audio.EqualizerPreset
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
                .padding(top = 8.dp, bottom = 24.dp) // Reduzido padding superior de 24dp para 8dp
        ) {
            // Header Compacto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Equalizador",
                        style = MaterialTheme.typography.headlineSmall, // Menor para economizar espaço
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Spectrum Visualizer mais fino
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp) // Reduzido de 120dp para 80dp
                    .padding(horizontal = 20.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                RealtimeSpectrum(
                    data = uiState.spectrumData,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp)) // Reduzido de 32dp para 16dp

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
            ModernPresetsSection(
                currentPresetId = uiState.currentPresetId,
                onPresetSelected = { presetId, gains ->
                    viewModel.applyPreset(EqualizerPreset(presetId, "", gains))
                }
            )

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

    val minGain = -15f
    val maxGain = 15f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp) // Largura um pouco maior para respiro
    ) {
        // Valor atual com destaque maior
        Surface(
            color = gainColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "${animatedGain.toInt()}dB",
                color = gainColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Slider Container.
        // Trilha e thumb são desenhados no MESMO Canvas, e o arraste é lido no
        // mesmo espaço de pixels dessa Box (0..size.height). Sem Slider() rotacionado
        // e sem "número mágico" de largura para compensar o padding interno do thumb
        // do Material3 — por isso o valor máximo (15dB) agora sempre corresponde
        // exatamente ao topo, e o mínimo (-15dB) exatamente à base.
        Box(
            modifier = Modifier
                .height(320.dp) // Aumentado de 200dp para 320dp
                .width(50.dp)
                .pointerInput(Unit) {
                    fun updateFromY(y: Float) {
                        val fraction = (1f - y / size.height).coerceIn(0f, 1f)
                        onGainChange(
                            (minGain + fraction * (maxGain - minGain)).coerceIn(minGain, maxGain)
                        )
                    }
                    awaitEachGesture {
                        // awaitFirstDown + drag (sem exigir touch slop) => o fader
                        // responde já no toque inicial, igual a um Slider comum.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateFromY(down.position.y)
                        drag(down.id) { change ->
                            updateFromY(change.position.y)
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track de fundo estilizada (estilo fader profissional) + thumb
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 12f
                val corner = 6f
                
                // Barra de fundo (sulco do fader)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.4f),
                    topLeft = Offset((size.width - trackWidth) / 2, 0f),
                    size = Size(trackWidth, size.height),
                    cornerRadius = CornerRadius(corner, corner)
                )

                // Linha central de referência
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f
                )
                
                // Marcas de escala
                for (i in 0..10) {
                    val y = (size.height / 10) * i
                    val lineWidth = if (i == 5) 20f else 10f
                    val alpha = if (i == 5) 0.5f else 0.2f
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset((size.width - lineWidth) / 2, y),
                        end = Offset((size.width + lineWidth) / 2, y),
                        strokeWidth = 2f
                    )
                }

                // Thumb: posição calculada a partir do MESMO gain e do MESMO
                // size.height usados acima, então ele sempre alcança as duas pontas.
                val fraction = ((animatedGain - minGain) / (maxGain - minGain)).coerceIn(0f, 1f)
                val thumbY = (size.height * (1f - fraction)).coerceIn(0f, size.height)
                val thumbHalfWidth = 22f
                val thumbHeight = 8f
                drawRoundRect(
                    color = gainColor,
                    topLeft = Offset(size.width / 2 - thumbHalfWidth, thumbY - thumbHeight / 2),
                    size = Size(thumbHalfWidth * 2, thumbHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Label da frequência
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
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
fun ModernPresetsSection(
    currentPresetId: String,
    onPresetSelected: (String, FloatArray) -> Unit
) {
    val presets = listOf(
        PresetData("flat", "Flat", FloatArray(10) { 0f }),
        PresetData("rock", "Rock", floatArrayOf(4f, 3f, 0f, -1f, -2f, 0f, 2f, 3f, 4f, 3f)),
        PresetData("pop", "Pop", floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f)),
        PresetData("jazz", "Jazz", floatArrayOf(3f, 2f, 0f, 2f, -2f, -2f, 0f, 2f, 3f, 4f)),
        PresetData("classical", "Classical", floatArrayOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f)),
        PresetData("bass_boost", "Bass Boost", floatArrayOf(8f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
        PresetData("treble_boost", "Treble Boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 2f, 4f, 6f, 8f, 10f))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Usar LazyRow para que os presets sejam roláveis e não quebrem o layout
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(presets) { _, preset ->
                PresetChip(
                    name = preset.name,
                    isSelected = currentPresetId == preset.id,
                    onClick = { 
                        android.util.Log.d("EQ", "Preset clicado: ${preset.id}")
                        onPresetSelected(preset.id, preset.gains) 
                    }
                )
            }
        }
    }
}

data class PresetData(val id: String, val name: String, val gains: FloatArray)

@Composable
fun PresetChip(name: String, isSelected: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) YTDownPurple else Color.White.copy(alpha = 0.1f)
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
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
        val totalGap = 4f
        val barWidth = (size.width - (barCount - 1) * totalGap) / barCount
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
            val x = i * (barWidth + totalGap)
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
                topLeft = Offset(x + 1, maxHeight - barHeight + 4),
                size = Size(barWidth - 2, 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}