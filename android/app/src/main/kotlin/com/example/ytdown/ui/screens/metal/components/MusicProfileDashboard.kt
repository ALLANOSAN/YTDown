package com.example.ytdown.ui.screens.metal.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdown.ui.screens.metal.models.*
import com.example.ytdown.ui.theme.*

/**
 * Dashboard do Perfil Musical - Versão Profissional com UI Models
 * 
 * Recebe apenas MusicProfileUiModel - completamente desacoplado do banco
 */

// =====================================================
// MAIN DASHBOARD
// =====================================================

@Composable
fun MusicProfileDashboard(
    profile: MusicProfileUiModel?,
    stats: com.example.ytdown.data.repository.metal.ListeningStatsResult?,
    modifier: Modifier = Modifier
) {
    // profile já é o UiModel correto - usar diretamente
    // Se tem stats, converter para UiModel também
    val uiModel = profile ?: stats?.toUiModel()
    
    when {
        uiModel == null || !uiModel.hasData -> {
            EmptyDashboard()
        }
        else -> {
            MusicProfileContent(uiModel = uiModel, modifier = modifier)
        }
    }
}

@Composable
private fun MusicProfileContent(
    uiModel: MusicProfileUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "Seu Perfil Musical",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        // Score Card + Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MusicScoreCard(
                score = uiModel.musicScore,
                description = uiModel.scoreDescription,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            QuickStatsCard(
                totalHours = uiModel.totalListeningHours,
                totalTracks = uiModel.totalTracksPlayed,
                uniqueArtists = uiModel.uniqueArtists,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Gêneros
        if (uiModel.favoriteGenres.isNotEmpty()) {
            GenreSection(genres = uiModel.favoriteGenres)
        }
        
        // Top Artistas
        if (uiModel.topArtists.isNotEmpty()) {
            TopArtistsSection(artists = uiModel.topArtists)
        }
        
        // Evolução Semanal
        if (uiModel.weeklyData.isNotEmpty()) {
            WeeklySection(weeklyData = uiModel.weeklyData)
        }
    }
}

@Composable
private fun EmptyDashboard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Seu perfil musical está sendo construído",
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ouça mais músicas para ver suas estatísticas",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =====================================================
// SCORE CARD
// =====================================================

@Composable
fun MusicScoreCard(
    score: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    
                    // Background circle
                    drawCircle(
                        color = SurfaceDark.copy(alpha = 0.5f),
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    // Progress arc
                    val sweepAngle = (score / 100f * 360f)
                    drawArc(
                        color = YTDownPurple,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                Text(
                    text = "$score",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickStatsCard(
    totalHours: String,
    totalTracks: Int,
    uniqueArtists: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatItem(
                icon = Icons.Default.Timer,
                label = "Tempo Total",
                value = totalHours
            )
            
            StatItem(
                icon = Icons.Default.MusicNote,
                label = "Músicas",
                value = totalTracks.toString()
            )
            
            StatItem(
                icon = Icons.Default.People,
                label = "Artistas",
                value = uniqueArtists.toString()
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = YTDownPurple,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// =====================================================
// GENRE SECTION
// =====================================================

@Composable
private fun GenreSection(genres: List<GenreUiModel>) {
    Column {
        Text(
            text = "Gêneros Preferidos",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        GenrePieChart(genres = genres)
    }
}

@Composable
fun GenrePieChart(genres: List<GenreUiModel>) {
    if (genres.isEmpty()) return
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chart
        Box(
            modifier = Modifier
                .size(180.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 32.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                
                var startAngle = -90f
                val total = genres.sumOf { it.percentage.toDouble() }.coerceAtLeast(1.0)
                
                genres.forEach { genre ->
                    val sweepAngle = (genre.percentage / total * 360).toFloat()
                    
                    drawArc(
                        color = Color(genre.color),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    
                    startAngle += sweepAngle
                }
            }
            
            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${genres.sumOf { it.percentage.toInt() }}%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "total",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
        
        // Legend
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(genres) { genre ->
                LegendChip(
                    color = Color(genre.color),
                    name = genre.name.take(10),
                    percentage = genre.percentage.toInt()
                )
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, name: String, percentage: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$name ($percentage%)",
                color = color,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =====================================================
// TOP ARTISTS SECTION
// =====================================================

@Composable
private fun TopArtistsSection(artists: List<ArtistUiModel>) {
    Column {
        Text(
            text = "Artistas Mais Ouvidos",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        artists.forEach { artist ->
            ArtistBarItem(artist = artist)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ArtistBarItem(artist: ArtistUiModel) {
    val barColor = when (artist.rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> YTDownPurple
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "#${artist.rank}",
            color = barColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(100.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(artist.progress)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(barColor, barColor.copy(alpha = 0.6f))
                        )
                    )
            )
        }
        
        Text(
            text = "${artist.playCount}",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

// =====================================================
// WEEKLY SECTION
// =====================================================

@Composable
private fun WeeklySection(weeklyData: Map<String, Int>) {
    Column {
        Text(
            text = "Esta Semana",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        WeeklyBarChart(data = weeklyData)
    }
}

@Composable
private fun WeeklyBarChart(data: Map<String, Int>) {
    val maxValue = data.values.maxOrNull() ?: 1
    val days = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // Group data by day name
        val sortedData = days.map { day ->
            data[day] ?: 0
        }
        
        sortedData.forEachIndexed { index, count ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height((count.toFloat() / maxValue * 60).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(YTDownPurple, YTDownPurple.copy(alpha = 0.4f))
                            )
                        )
                )
                
                Text(
                    text = days[index],
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}