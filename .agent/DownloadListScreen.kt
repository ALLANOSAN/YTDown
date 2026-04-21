package com.example.ytdown.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.components.DownloadItemRow

@Composable
fun DownloadListScreen(viewModel: DownloadViewModel) {
    val downloads by viewModel.downloads.collectAsState()

    LazyColumn {
        items(
            items = downloads,
            key = { it.id }
        ) { item ->
            DownloadItemRow(item)
        }
    }
}