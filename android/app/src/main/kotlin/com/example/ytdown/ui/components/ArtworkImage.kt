package com.example.ytdown.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size

/**
 * Componente padronizado para exibir artes de álbum/artista.
 * Força o redimensionamento para 300x300 e aplica cache otimizado.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier.size(64.dp),
    contentDescription: String? = null
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .size(Size(300, 300)) // Força downscaling para 300x300
            .crossfade(true)
            .diskCacheKey(url)
            .memoryCacheKey(url)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}
