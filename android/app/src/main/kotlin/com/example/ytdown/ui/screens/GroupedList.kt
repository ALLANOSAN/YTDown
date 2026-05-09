package com.example.ytdown.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.ui.components.StaggeredVerticalEntrance
import com.example.ytdown.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedList(
    groups: Map<String, List<DownloadItemEntity>>,
    icon: ImageVector,
    onNavigate: (String) -> Unit,
    libraryViewModel: LibraryViewModel,
    isArtistGroup: Boolean = false,
    onLongClick: ((String, String?) -> Unit)? = null
) {
    if (groups.isEmpty()) {
        EmptyLibraryMessage()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val keys = groups.keys.toList()
        itemsIndexed(items = keys, key = { _, it -> it }) { index, key ->
            StaggeredVerticalEntrance(index = index) {
                val groupItems = groups[key] ?: emptyList()
                var artwork = groupItems.firstOrNull { !it.albumImageUrl.isNullOrEmpty() }?.albumImageUrl
                if (isArtistGroup) { artwork = groupItems.firstOrNull { !it.artistImageUrl.isNullOrEmpty() }?.artistImageUrl }
                if (artwork.isNullOrEmpty()) { artwork = groupItems.firstOrNull { !it.thumbnailPath.isNullOrEmpty() }?.thumbnailPath }

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(
                            onClick = { libraryViewModel.triggerHapticClick(); onNavigate(key) },
                            onLongClick = { libraryViewModel.triggerHapticHeavy(); onLongClick?.invoke(key, artwork) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artwork != null) {
                        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Surface(modifier = Modifier.size(60.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF1A1A1A)) {
                            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = TextSecondary) }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${groupItems.size} músicas", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
