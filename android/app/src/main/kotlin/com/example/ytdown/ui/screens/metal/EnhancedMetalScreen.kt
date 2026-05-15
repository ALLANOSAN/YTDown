package com.example.ytdown.ui.screens.metal

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabPosition
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.ytdown.data.local.metal.entities.MetalArtistEntity
import com.example.ytdown.services.DiscoveredArtist
import com.example.ytdown.ui.EnhancedMetalViewModel
import com.example.ytdown.ui.MetalUIState
import com.example.ytdown.ui.components.metal.ShimmerArtistCard
import com.example.ytdown.ui.components.metal.ShimmerInitialLoading
import com.example.ytdown.ui.screens.metal.components.MusicProfileDashboard
import com.example.ytdown.ui.screens.metal.models.toUiModel
import com.example.ytdown.ui.theme.*

/**
 * Tela Enterprise do Sistema Metal
 * 
 * Implementa:
 * - Paging 3 com LazyPagingItems
 * - Cache Offline First
 * - Shimmer Loading real
 * - Perfil Musical Visual
 * - Estados de UI completos
 * - Animações profissionais
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMetalScreen(
    viewModel: EnhancedMetalViewModel = hiltViewModel(),
    onBandClick: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val artists = viewModel.artistsPagingFlow.collectAsLazyPagingItems()
    
    // Estado para aba atual
    var currentTab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        TabItem("Descobrir", Icons.Default.Explore),
        TabItem("Biblioteca", Icons.Default.LibraryMusic),
        TabItem("Perfil", Icons.Default.Person)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header
        MetalHeader(
            onRefresh = { viewModel.refresh() },
            isLoading = uiState is MetalUIState.Loading || uiState is MetalUIState.Initializing
        )
        
        // Tabs
        PrimaryScrollableTabRow(
            selectedTabIndex = currentTab,
            containerColor = Color.Black,
            contentColor = YTDownPurple,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = currentTab == index,
                    onClick = { currentTab = index },
                    text = {
                        Text(
                            tab.title,
                            color = if (currentTab == index) YTDownPurple else TextSecondary
                        )
                    },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = tab.title,
                            tint = if (currentTab == index) YTDownPurple else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
        
        // Conteúdo
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is MetalUIState.Initializing, is MetalUIState.Loading -> {
                    ShimmerInitialLoading()
                }
                
                is MetalUIState.Success -> {
                    when (currentTab) {
                        0 -> DiscoveryTab(
                            artists = artists,
                            discoveredStyles = state.detectedStyles,
                            recommendedArtists = state.recommendedArtists,
                            isOffline = state.isOffline,
                            onBandClick = onBandClick,
                            onDownload = { viewModel.downloadBand(it) }
                        )
                        1 -> LibraryTab(
                            artists = artists,
                            cachedCount = state.cachedArtistCount,
                            isOffline = state.isOffline,
                            onBandClick = onBandClick,
                            onFavoriteToggle = { viewModel.toggleFavorite(it) }
                        )
                        2 -> ProfileTab(
                            profile = state.profile,
                            stats = state.stats
                        )
                    }
                }
                
                is MetalUIState.Error -> {
                    ErrorContent(
                        message = state.message,
                        isOffline = state.isOffline,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetalHeader(
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = YTDownPurple,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    "Descoberta Dinâmica",
                    color = YTDownPurple,
                    fontSize = 12.sp
                )
            }
        }
        
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = YTDownPurple,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Atualizar",
                    tint = YTDownPurple
                )
            }
        }
    }
}

@Composable
private fun DiscoveryTab(
    artists: androidx.paging.compose.LazyPagingItems<MetalArtistEntity>,
    discoveredStyles: List<com.example.ytdown.services.DiscoveredStyle>,
    recommendedArtists: List<DiscoveredArtist>,
    isOffline: Boolean,
    onBandClick: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status offline
        if (isOffline) {
            item {
                OfflineBanner()
            }
        }
        
        // Estilos detectados
        if (discoveredStyles.isNotEmpty()) {
            item {
                DetectedStylesRow(styles = discoveredStyles)
            }
        }
        
        // Artistas do Paging
        when (artists.loadState.refresh) {
            is LoadState.Loading -> {
                items(6) {
                    ShimmerArtistCard()
                }
            }
            is LoadState.Error -> {
                item {
                    ErrorItem(
                        message = "Erro ao carregar artistas",
                        onRetry = { artists.retry() }
                    )
                }
            }
            else -> {
                items(
                    count = artists.itemCount,
                    key = artists.itemKey { it.mbid }
                ) { index ->
                    val artist = artists[index]
                    artist?.let {
                        ArtistCard(
                            artist = it,
                            onClick = { onBandClick(it.name) },
                            onDownload = { onDownload(it.name) }
                        )
                    }
                }
                
                // Loading append
                if (artists.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YTDownPurple,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    artists: androidx.paging.compose.LazyPagingItems<MetalArtistEntity>,
    cachedCount: Int,
    isOffline: Boolean,
    onBandClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Info do cache
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$cachedCount artistas em cache",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (isOffline) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Yellow.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "Modo Offline",
                            color = Color.Yellow,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        // Artistas
        items(
            count = artists.itemCount,
            key = artists.itemKey { it.mbid }
        ) { index ->
            val artist = artists[index]
            artist?.let {
                LibraryArtistCard(
                    artist = it,
                    onClick = { onBandClick(it.name) },
                    onFavoriteToggle = { onFavoriteToggle(it.mbid) }
                )
            }
        }
    }
}

@Composable
private fun ProfileTab(
    profile: com.example.ytdown.data.local.metal.entities.MusicProfileEntity?,
    stats: com.example.ytdown.data.repository.metal.ListeningStatsResult?
) {
    // Usar os mappers para converter para UI Model
    val uiModel = when {
        stats != null -> stats.toUiModel()
        profile != null -> profile.toUiModel()
        else -> null
    }
    
    MusicProfileDashboard(
        profile = uiModel,
        stats = null // Já convertido acima
    )
}

// =====================================================
// COMPONENTES
// =====================================================

data class TabItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Yellow.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Modo Offline - Usando dados em cache",
                color = Color.Yellow,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DetectedStylesRow(styles: List<com.example.ytdown.services.DiscoveredStyle>) {
    Column {
        Text(
            "Estilos Detectados",
            color = YTDownPurple,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(styles) { style ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = YTDownPurple.copy(alpha = 0.2f)
                ) {
                    Text(
                        style.name,
                        color = YTDownPurple,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: MetalArtistEntity,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(YTDownPurple.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (artist.imageUrl != null) {
                    AsyncImage(
                        model = artist.imageUrl,
                        contentDescription = artist.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = YTDownPurple
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    artist.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                artist.country?.let {
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
                        Text(it, color = TextSecondary, fontSize = 11.sp)
                    }
                }
                
                if (artist.tagsJson.isNotBlank() && artist.tagsJson != "[]") {
                    Text(
                        artist.getTagsList().take(3).joinToString(", "),
                        color = YTDownPurple,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Score
            if (artist.compatibilityScore > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = YTDownPurple.copy(alpha = 0.25f)
                ) {
                    Text(
                        "${artist.compatibilityScore.toInt()}%",
                        color = YTDownPurple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Baixar",
                    tint = YTDownPurple
                )
            }
        }
    }
}

@Composable
private fun LibraryArtistCard(
    artist: MetalArtistEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(YTDownPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = YTDownPurple)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    artist.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${artist.playCount} plays",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (artist.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (artist.isFavorite) Color.Red else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ErrorItem(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Red.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, color = Color.Red, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
            ) {
                Text("Tentar novamente")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isOffline: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            message,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = YTDownPurple)
        ) {
            Text("Tentar novamente")
        }
        
        if (isOffline) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Verifique sua conexão",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}