package com.example.ytdown.ui.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerLoadingList(
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    val transition = rememberInfiniteTransition()
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(Color.DarkGray.copy(alpha = 0.08f), Color.DarkGray.copy(alpha = 0.18f), Color.DarkGray.copy(alpha = 0.08f)),
        start = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f),
        end = androidx.compose.ui.geometry.Offset(x = 200f * progress.value, y = 200f * progress.value)
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp)),
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
