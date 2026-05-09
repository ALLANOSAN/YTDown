package com.example.ytdown.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@Composable
fun LibrarySearchBar(
    query: String = "", 
    onQueryChange: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp)),
            color = Color(0xFF1A1A1A)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Buscar na biblioteca...", color = TextSecondary)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
