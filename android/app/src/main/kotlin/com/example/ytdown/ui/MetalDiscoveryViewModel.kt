package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.MetalDiscoveryRepository
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.services.MatchLevel
import com.example.ytdown.services.UserMetalProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import javax.inject.Inject

/**
 * Estado completo da UI de Descoberta de Metal
 */
data class MetalDiscoveryUIState(
    // Perfil do usuário
    val userProfile: UserMetalProfile? = null,
    val isAnalyzingProfile: Boolean = false,
    
    // Bandas recomendadas
    val recommendations: List<RankedBand> = emptyList(),
    val isLoadingRecommendations: Boolean = false,
    
    // UI states
    val isInitialLoading: Boolean = true,
    val error: String? = null,
    
    // Estatísticas
    val totalBandsFound: Int = 0,
    val currentFilter: MetalFilter = MetalFilter.ALL,
    
    // Ações do usuário
    val lastDownloadedBand: String? = null
)

/**
 * Filtros de gênero disponíveis
 */
enum class MetalFilter {
    ALL,
    POWER_METAL,
    BLACK_METAL,
    DEATH_METAL,
    THRASH_METAL,
    DOOM_METAL,
    SYMPHONIC,
    PROGRESSIVE,
    METALCORE
}

/**
 * Extensão do modelo RankedBand para incluir mais informações
 */
data class RankedBand(
    val mbid: String,
    val name: String,
    val country: String?,
    val genre: String?,
    val tags: List<String>,
    val matchScore: Int,
    val matchedTags: List<String>,
    val matchLevel: MatchLevel,
    val isActive: Boolean
)

@HiltViewModel
class MetalDiscoveryViewModel @Inject constructor(
    private val discoveryRepository: MetalDiscoveryRepository,
    private val downloadRepository: DownloadRepository,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetalDiscoveryUIState())
    val uiState: StateFlow<MetalDiscoveryUIState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    /**
     * Carrega dados iniciais - análise do perfil + recomendações
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitialLoading = true, error = null) }

            try {
                // Passo 1: Analisar perfil do usuário
                _uiState.update { it.copy(isAnalyzingProfile = true) }
                
                val profile = discoveryRepository.analyzeUserProfileWithFallback()
                
                _uiState.update { 
                    it.copy(
                        userProfile = profile,
                        isAnalyzingProfile = false
                    ) 
                }

                // Passo 2: Obter nomes das bandas existentes na biblioteca
                val existingBands = downloadRepository.stream().first()
                    .mapNotNull { it.artist?.toString()?.lowercase() }
                    .toSet()

                // Passo 3: Buscar recomendações
                _uiState.update { it.copy(isLoadingRecommendations = true) }
                
                val recommendedBands = discoveryRepository.discoverSimilarBands(
                    userProfile = profile,
                    excludeExistingBandNames = existingBands
                )

                // Converter para RankedBand
                val rankedBands = recommendedBands.map { band ->
                    RankedBand(
                        mbid = band.mbid,
                        name = band.name,
                        country = band.country,
                        genre = band.genre,
                        tags = band.tags,
                        matchScore = band.similarityScore,
                        matchedTags = band.matchedTags,
                        matchLevel = band.matchLevel,
                        isActive = band.isActive
                    )
                }

                _uiState.update {
                    it.copy(
                        recommendations = rankedBands,
                        isLoadingRecommendations = false,
                        isInitialLoading = false,
                        totalBandsFound = rankedBands.size,
                        error = if (rankedBands.isEmpty()) "Nenhuma banda encontrada. Tente adicionar mais músicas à sua biblioteca." else null
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        error = "Erro ao carregar recomendações: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Recarrega as recomendações
     */
    fun refreshRecommendations() {
        loadInitialData()
    }

    /**
     * Aplica um filtro de gênero
     */
    fun applyFilter(filter: MetalFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentFilter = filter, isLoadingRecommendations = true) }
            
            val allRecommendations = _uiState.value.recommendations
            val filteredRecommendations = filterRecommendations(allRecommendations, filter)
            
            _uiState.update {
                it.copy(
                    recommendations = filteredRecommendations,
                    isLoadingRecommendations = false,
                    totalBandsFound = filteredRecommendations.size
                )
            }
        }
    }

    private fun filterRecommendations(bands: List<RankedBand>, filter: MetalFilter): List<RankedBand> {
        if (filter == MetalFilter.ALL) return bands
        
        val filterKeywords = getFilterKeywords(filter)
        
        return bands.filter { band ->
            val searchText = "${band.name} ${band.genre} ${band.tags.joinToString(" ")}".lowercase()
            filterKeywords.any { keyword -> searchText.contains(keyword) }
        }.sortedByDescending { it.matchScore }
    }

    private fun getFilterKeywords(filter: MetalFilter): List<String> = when (filter) {
        MetalFilter.POWER_METAL -> listOf("power", "euro power")
        MetalFilter.BLACK_METAL -> listOf("black")
        MetalFilter.DEATH_METAL -> listOf("death", "slam")
        MetalFilter.THRASH_METAL -> listOf("thrash", "speed")
        MetalFilter.DOOM_METAL -> listOf("doom", "stoner")
        MetalFilter.SYMPHONIC -> listOf("symphonic")
        MetalFilter.PROGRESSIVE -> listOf("progressive", "prog", "technical")
        MetalFilter.METALCORE -> listOf("metalcore", "deathcore")
        MetalFilter.ALL -> emptyList()
    }

    /**
     * Baixa as melhores músicas de uma banda
     */
    fun downloadBand(bandName: String) {
        viewModelScope.launch {
            try {
                val result = discoveryRepository.downloadBandBestSongs(bandName)
                
                _uiState.update {
                    it.copy(lastDownloadedBand = bandName)
                }
                
                // Mostrar feedback (o estado pode ser usado na UI)
                when (result) {
                    is com.example.ytdown.core.infrastructure.DownloadResult.Success -> {
                        // Sucesso - o download foi agendado
                    }
                    is com.example.ytdown.core.infrastructure.DownloadResult.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao baixar: ${e.message}") }
            }
        }
    }

    /**
     * Baixa um álbum específico
     */
    fun downloadAlbum(bandName: String, albumName: String, year: String? = null) {
        viewModelScope.launch {
            try {
                val result = discoveryRepository.downloadAlbum(bandName, albumName, year)
                
                when (result) {
                    is com.example.ytdown.core.infrastructure.DownloadResult.Success -> {
                        // Sucesso
                    }
                    is com.example.ytdown.core.infrastructure.DownloadResult.Error -> {
                        _uiState.update { it.copy(error = result.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao baixar álbum: ${e.message}") }
            }
        }
    }

    /**
     * Limpa erros
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Carrega mais recomendações (descobrir mais)
     */
    fun discoverMore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecommendations = true) }
            
            val currentProfile = _uiState.value.userProfile ?: return@launch
            
            // Obter bandas já recomendadas para evitar duplicatas
            val existingMbids = _uiState.value.recommendations.map { it.mbid }.toSet()
            val existingNames = _uiState.value.recommendations.map { it.name.lowercase() }.toSet()
            
            val existingBands = downloadRepository.stream().first()
                .mapNotNull { it.artist?.toString()?.lowercase() }
                .toSet()
            
            // Buscar mais bandas
            val moreBands = discoveryRepository.discoverSimilarBands(
                userProfile = currentProfile,
                excludeExistingBandNames = existingBands + existingNames
            ).filter { it.mbid !in existingMbids }
            
            val newRanked = moreBands.map { band ->
                RankedBand(
                    mbid = band.mbid,
                    name = band.name,
                    country = band.country,
                    genre = band.genre,
                    tags = band.tags,
                    matchScore = band.similarityScore,
                    matchedTags = band.matchedTags,
                    matchLevel = band.matchLevel,
                    isActive = band.isActive
                )
            }
            
            _uiState.update {
                it.copy(
                    recommendations = (it.recommendations + newRanked).take(60),
                    isLoadingRecommendations = false,
                    totalBandsFound = it.recommendations.size + newRanked.size
                )
            }
        }
    }
}