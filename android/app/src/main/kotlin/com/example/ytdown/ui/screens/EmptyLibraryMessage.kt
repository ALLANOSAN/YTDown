package com.example.ytdown.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ytdown.ui.theme.TextSecondary

@Composable
fun EmptyLibraryMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nenhum item encontrado.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
