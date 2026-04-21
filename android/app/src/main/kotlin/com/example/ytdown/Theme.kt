package com.example.ytdown.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RedMain,
    background = DarkGrey,
    surface = SurfaceGrey,
    onPrimary = White,
    onBackground = White,
    onSurface = White,
    error = ErrorRed
)

@Composable
fun YTDownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}