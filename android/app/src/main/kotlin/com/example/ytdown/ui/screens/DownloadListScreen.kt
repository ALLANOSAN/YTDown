package com.example.ytdown.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.components.DownloadItemRow

@Composable
fun DownloadListScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = downloads,
                key = { it.id }
            ) { item ->
                DownloadItemRow(item)
            }
        }
    }
}