package com.example.ytdown.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.services.EqualizerManager
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.theme.YTDownPurple

@Composable
fun EqualizerScreen(
    playerViewModel: PlayerViewModel,
    equalizerManager: EqualizerManager
) {
    LaunchedEffect(Unit) {
        val sessionId = playerViewModel.playerManager.getAudioSessionId()
        equalizerManager.initEffects(sessionId)
    }

    val numBands = equalizerManager.getNumberOfBands().toInt()
    val (minLevel, maxLevel) = equalizerManager.getBandLevelRange()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Equalizador", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Bandas de Frequência", style = MaterialTheme.typography.titleMedium, color = YTDownPurple)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (i in 0 until numBands) {
                BandSlider(
                    bandIndex = i.toShort(),
                    minLevel = minLevel.toFloat(),
                    maxLevel = maxLevel.toFloat(),
                    equalizerManager = equalizerManager
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray)
        Spacer(modifier = Modifier.height(24.dp))

        // --- Bass Boost ---
        Text("Bass Boost", style = MaterialTheme.typography.titleMedium, color = YTDownPurple)
        var bassLevel by remember { mutableStateOf(equalizerManager.getBassBoostStrength().toFloat()) }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = bassLevel,
                onValueChange = { 
                    bassLevel = it
                    equalizerManager.setBassBoostStrength(it.toInt().toShort()) 
                },
                valueRange = 0f..1000f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(activeTrackColor = YTDownPurple)
            )
            Text("${(bassLevel / 10).toInt()}%", color = Color.White, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Loudness Enhancer ---
        Text("Loudness Enhancer (Ganho)", style = MaterialTheme.typography.titleMedium, color = YTDownPurple)
        var loudnessLevel by remember { mutableStateOf(equalizerManager.getTargetGain().toFloat()) }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = loudnessLevel,
                onValueChange = { 
                    loudnessLevel = it
                    equalizerManager.setTargetGain(it.toInt()) 
                },
                valueRange = 0f..2000f, // 0 a 20dB (em mB)
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(activeTrackColor = YTDownPurple)
            )
            Text("${(loudnessLevel / 100).toInt()}dB", color = Color.White, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold)
        }
        
        Text(
            "Útil para normalizar músicas com volume baixo.",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun BandSlider(bandIndex: Short, minLevel: Float, maxLevel: Float, equalizerManager: EqualizerManager) {
    var level by remember { mutableStateOf(equalizerManager.getBandLevel(bandIndex).toFloat()) }
    val freq = equalizerManager.getCenterFreq(bandIndex) / 1000 // em Hz

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text("${(level / 100).toInt()}dB", fontSize = 10.sp, color = Color.White)
        Slider(
            value = level,
            onValueChange = { 
                level = it
                equalizerManager.setBandLevel(bandIndex, it.toInt().toShort()) 
            },
            valueRange = minLevel..maxLevel,
            modifier = Modifier.height(180.dp).width(36.dp)
        )
        Text(if (freq >= 1000) "${freq/1000}kHz" else "${freq}Hz", fontSize = 10.sp, color = Color.Gray)
    }
}
