package com.example.ytdown.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FormatSelectionSheet(
    title: String,
    formats: List<String>,
    qualities: List<String>,
    selectedFormat: String,
    selectedQuality: String,
    onFormatSelected: (String) -> Unit,
    onQualitySelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "Formato", style = MaterialTheme.typography.bodyLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(formats) { format ->
                    val isSelected = format == selectedFormat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFormatSelected(format) }
                            .padding(vertical = 10.dp)
                    ) {
                        var textColor = MaterialTheme.colorScheme.onSurface
                        if (isSelected) {
                            textColor = MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = format,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Qualidade", style = MaterialTheme.typography.bodyLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(qualities) { quality ->
                    val isSelected = quality == selectedQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                            .padding(vertical = 10.dp)
                    ) {
                        var textColor = MaterialTheme.colorScheme.onSurface
                        if (isSelected) {
                            textColor = MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onCancel) {
                    Text(text = "Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onConfirm) {
                    Text(text = "Confirmar")
                }
            }
        }
    }
}
