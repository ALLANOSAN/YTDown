package com.example.ytdown.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.core.audio.BassPlaybackEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay

@Composable
fun SpectrumVisualizer(
    audioEngine: BassPlaybackEngine,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White
) {
    val fftBuffer = remember { ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder()) }
    val fft = remember { FloatArray(1024) }
    var magnitudes by remember { mutableStateOf(FloatArray(64) { 0f }) }

    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying) {
                fftBuffer.clear()
                audioEngine.getFftData(fftBuffer)
                fftBuffer.asFloatBuffer().get(fft)
                
                // Agrupar FFT em 64 barras
                val newMagnitudes = FloatArray(64)
                for (i in 0 until 64) {
                    var sum = 0f
                    for (j in 0 until 16) {
                        sum += Math.abs(fft[i * 16 + j])
                    }
                    newMagnitudes[i] = (sum / 16f) * 800f // Ajuste de sensibilidade
                }
                magnitudes = newMagnitudes
            } else {
                magnitudes = FloatArray(64) { 0f }
            }
            delay(30) // ~33 FPS
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val barWidth = size.width / 64f
        for (i in 0 until 64) {
            val barHeight = magnitudes[i].coerceIn(2f, size.height)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight)
            )
        }
    }
}
