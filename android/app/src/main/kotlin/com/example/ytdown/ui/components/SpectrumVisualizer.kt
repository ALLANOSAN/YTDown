package com.example.ytdown.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SpectrumVisualizer(
    spectrumData: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White
) {
    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val barCount = spectrumData.size.coerceAtMost(64)
        val barWidth = size.width / barCount

        for (i in 0 until barCount) {
            val magnitude = if (isPlaying) spectrumData.getOrElse(i) { 0f } else 0f
            val barHeight = magnitude.coerceIn(2f, size.height)

            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 2f, barHeight)
            )
        }
    }
}