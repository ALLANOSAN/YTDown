package com.example.ytdown.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.core.audio.EqualizerViewModel

private val frequencies = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // FFT Visualizer Real
        RealtimeSpectrum(uiState.spectrumData)
        
        Spacer(modifier = Modifier.height(32.dp))

        // Sliders Verticais do Equalizador
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(frequencies) { index, freq ->
                VerticalBandSlider(
                    label = "${freq}Hz",
                    gain = uiState.bandGains[index],
                    onGainChange = { viewModel.setBandGain(index, it) }
                )
            }
        }

        // Preamp Global
        PreampControl(
            value = uiState.preamp,
            onValueChange = { viewModel.updatePreamp(it) }
        )
    }
}

@Composable
fun VerticalBandSlider(label: String, gain: Float, onGainChange: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight().width(44.dp)
    ) {
        Text("${gain.toInt()}dB", fontSize = 10.sp, color = Color.White)
        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -15f..15f,
            modifier = Modifier.weight(1f).graphicsLayer { rotationZ = -90f }
        )
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun RealtimeSpectrum(data: FloatArray) {
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        data.forEachIndexed { i, value ->
            val barWidth = 12f
            val spacing = 8f
            val x = i * (barWidth + spacing)
            drawLine(
                color = YTDownPurple,
                start = Offset(x, size.height),
                end = Offset(x, size.height - (value * size.height).coerceAtLeast(2f)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun PreampControl(value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Preamp", color = Color.White)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -15f..15f,
            modifier = Modifier.weight(1f)
        )
        Text("${value.toInt()}dB", color = YTDownPurple)
    }
}
