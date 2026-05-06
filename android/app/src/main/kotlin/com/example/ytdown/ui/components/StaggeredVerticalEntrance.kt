package com.example.ytdown.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun StaggeredVerticalEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    val state = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    AnimatedVisibility(
        visibleState = state,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = (index * 50).coerceAtMost(300) // Stagger effect
            ),
            initialOffsetY = { 100 } // Slide up from 100px
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = (index * 50).coerceAtMost(300)
            )
        )
    ) {
        content()
    }
}
