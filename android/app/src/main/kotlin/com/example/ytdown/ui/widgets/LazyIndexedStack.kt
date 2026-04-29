package com.example.ytdown.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LazyIndexedStack(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content(index)
    }
}
