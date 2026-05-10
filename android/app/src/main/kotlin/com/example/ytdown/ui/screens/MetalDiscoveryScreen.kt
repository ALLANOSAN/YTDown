package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ytdown.ui.MetalDiscoveryViewModel
import com.example.ytdown.ui.RankedBand
import com.example.ytdown.ui.theme.SurfaceDark
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple

@Composable
fun MetalDiscoveryScreen(
    viewModel: MetalDiscoveryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.suggestions.isEmpty() && !state.isLoading) {
            viewModel.loadSuggestions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // ── Cabeçalho ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Descoberta",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            IconButton(
                onClick = { viewModel.loadSuggestions() },
                enabled = !state.isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = YTDownPurple)
            }
        }

        Text(
            "via MusicBrainz",
            color = YTDownPurple,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Artistas-semente usados nesta rodada
        if (state.seedArtists.isNotEmpty()) {
            Text(
                "Baseado em: ${state.seedArtists.joinToString(", ")}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Tags que formam o perfil musical detectado
        if (state.profileTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.profileTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = YTDownPurple.copy(alpha = 0.15f)
                    ) {
                        Text(
                            tag,
                            color = YTDownPurple,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ── Conteúdo ───────────────────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YTDownPurple)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Consultando MusicBrainz…",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        if (state.profileTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Buscando por: ${state.profileTags.take(3).joinToString(", ")}",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(state.error!!, color = Color.Red, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSuggestions() },
                            colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                        ) { Text("Tentar novamente") }
                    }
                }
            }

            state.suggestions.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma sugestão encontrada.",
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Text(
                    "${state.suggestions.size} bandas · ordenadas por compatibilidade",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.suggestions) { ranked ->
                        DiscoveryBandRow(
                            ranked = ranked,
                            onDownload = { viewModel.downloadBand(ranked.band.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryBandRow(
    ranked: RankedBand,
    onDownload: () -> Unit
) {
    var downloadTriggered by remember { mutableStateOf(false) }
    val band = ranked.band

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {

                // Nome + badge de compatibilidade
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        band.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (ranked.matchScore >= 2) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YTDownPurple.copy(alpha = 0.25f)
                        ) {
                            Text(
                                "${ranked.matchScore} tags",
                                color = YTDownPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Gênero principal
                Text(
                    band.genre ?: band.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Metal",
                    color = YTDownPurple,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // País
                if (!band.country.isNullOrBlank()) {
                    Text(
                        band.country,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Tags em comum com o perfil do usuário
                if (ranked.matchTags.isNotEmpty()) {
                    Text(
                        ranked.matchTags.joinToString(", "),
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onDownload()
                    downloadTriggered = true
                },
                enabled = !downloadTriggered,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (downloadTriggered) Color.DarkGray else YTDownPurple
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (downloadTriggered) "Na Fila" else "Baixar",
                    fontSize = 12.sp
                )
            }
        }
    }
}
