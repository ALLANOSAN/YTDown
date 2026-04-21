package com.example.ytdown.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytdown.core.domain.DownloadItemEntity

@Composable
fun DownloadItemRow(item: DownloadItemEntity) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            DownloadProgress(item.progress.toFloat(), item.status)
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float, status: String) {
    LinearProgressIndicator(
        progress = progress,
        modifier = Modifier.fillMaxWidth()
    )
    Text(text = "Status: $status", style = MaterialTheme.typography.bodySmall)
}