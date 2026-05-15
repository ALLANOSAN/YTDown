package com.example.ytdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.ytdown.services.DiscoveredArtist
import com.example.ytdown.services.DiscoveredStyle
import com.example.ytdown.ui.DynamicMetalViewModel
import com.example.ytdown.ui.theme.*

/**
 * Tela de Descoberta Dinâmica de Metal
 * 
 * Sistema de descoberta musical que NÃO usa gêneros hardcoded.
 * Analisa a biblioteca do usuário e detecta estilos automaticamente.
 */
@Composable
fun DynamicMetalScreen(
    viewModel: DynamicMetalViewModel,
    onBack: () -> Unit,
    onBandClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Metal",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = YTDownPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Descoberta Dinâmica",
                        color = YTDownPurple,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = YTDownPurple,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = YTDownPurple)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado de carregamento
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YTDownPurple)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Analisando sua biblioteca...",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Detectando estilos automaticamente",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.error!!, color = Color.Red, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
                        ) {
                            Text("Tentar novamente")
                        }
                    }
                }
            }

            else -> {
                // Estilos detectados automaticamente
                if (state.detectedStyles.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = YTDownPurple.copy(alpha = 0.15f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = YTDownPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Estilos Detectados",
                                    color = YTDownPurple,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Baseado em ${state.analyzedArtistsCount} artistas da sua biblioteca",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tags descobertas dinamicamente
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                state.detectedStyles.take(5).forEach { style ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = YTDownPurple
                                    ) {
                                        Text(
                                            style.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Estatísticas
                Text(
                    "${state.recommendedArtists.size} bandas descobertas",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Lista de artistas descobertos
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.recommendedArtists) { artist ->
                        DynamicArtistCard(
                            artist = artist,
                            onClick = { onBandClick(artist.name) },
                            onDownload = { viewModel.downloadBand(artist.name) }
                        )
                    }

                    // Botão descobrir mais
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.discoverMore() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isDiscoveringMore,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = YTDownPurple)
                        ) {
                            if (state.isDiscoveringMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = YTDownPurple,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isDiscoveringMore) "Buscando..." else "Descobrir mais")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card de artista descoberto dinamicamente
 */
@Composable
fun DynamicArtistCard(
    artist: DiscoveredArtist,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    var downloadTriggered by remember { mutableStateOf(false) }

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
                // Nome - clicável
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Text(
                        artist.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )

                    // Score
                    if (artist.matchScore > 30) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = YTDownPurple.copy(alpha = 0.25f)
                        ) {
                            Text(
                                "${artist.matchScore}%",
                                color = YTDownPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Tags em comum
                if (artist.matchedTags.isNotEmpty()) {
                    Text(
                        "Similar em: ${artist.matchedTags.take(3).joinToString(", ")}",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // País
                artist.country?.let { country ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(country, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Tags da API
                if (artist.tags.isNotEmpty()) {
                    Text(
                        artist.tags.take(4).joinToString(", "),
                        color = YTDownPurple,
                        fontSize = 11.sp,
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
                    if (downloadTriggered) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (downloadTriggered) "Enviado" else "Baixar",
                    fontSize = 12.sp
                )
            }
        }
    }
}