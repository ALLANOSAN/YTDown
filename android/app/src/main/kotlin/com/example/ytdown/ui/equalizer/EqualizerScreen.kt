package com.example.ytdown.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ytdown.core.audio.EqualizerViewModel

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Equalizador Pro", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        
        // Visualizador Spectrum
        SpectrumAnalyzer(state.spectrumData)

        Spacer(modifier = Modifier.height(24.dp))

        // Grid de Bandas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            state.bandGains.forEachIndexed { index, gain ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfessionalVerticalSlider(
                        value = gain,
                        onValueChange = { viewModel.updateBandGain(index, it) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${gain.toInt()}dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Cyan
                    )
                }
            }
        }
    }
}

@Composable
fun SpectrumAnalyzer(spectrumData: FloatArray) {
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val barWidth = size.width / 64
        spectrumData.take(64).forEachIndexed { i, value ->
            val barHeight = (value * size.height * 2).coerceIn(0f, size.height)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Cyan, Color.Blue)),
                topLeft = Offset(i * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight)
            )
        }
    }
}

@Composable
fun ProfessionalVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableStateOf(0f) }

    Canvas(modifier = modifier
        .fillMaxHeight()
        .width(40.dp)
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val newY = change.position.y
                val normalizedY = (1f - (newY / height)).coerceIn(0f, 1f)
                val newValue = (normalizedY * 30f) - 15f
                onValueChange(newValue)
            }
        }
    ) {
        height = size.height
        val centerX = size.width / 2
        val thumbY = size.height * (1f - ((value + 15f) / 30f))

        // Trilho
        drawLine(
            color = Color.DarkGray,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Thumb
        drawCircle(
            brush = Brush.verticalGradient(listOf(Color.Cyan, Color.Blue)),
            radius = 12.dp.toPx(),
            center = Offset(centerX, thumbY)
        )
    }
}
