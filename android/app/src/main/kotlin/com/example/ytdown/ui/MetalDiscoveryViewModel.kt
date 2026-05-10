package com.example.ytdown.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.DownloadScheduler
import com.example.ytdown.core.domain.*
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.services.MBBand
import com.example.ytdown.services.MusicBrainzService
import com.example.ytdown.services.TagEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Estado da UI
// ─────────────────────────────────────────────────────────────────────────────

data class MetalDiscoveryUIState(
    val suggestions: List<RankedBand> = emptyList(),
    val seedArtists: List<String> = emptyList(),
    val profileTags: List<String> = emptyList(),   // tags que definem o gosto do usuário
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Banda sugerida com metadados de relevância.
 *
 * @param band        Dados da banda vindos do MusicBrainz.
 * @param matchScore  Quantas das tags do perfil do usuário essa banda compartilha.
 * @param matchTags   Quais tags em comum (para exibir na UI).
 */
data class RankedBand(
    val band: MBBand,
    val matchScore: Int,
    val matchTags: List<String>
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class MetalDiscoveryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val musicBrainzService: MusicBrainzService,
    private val scheduler: DownloadScheduler,
    private val storageResolver: StorageResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetalDiscoveryUIState())
    val uiState = _uiState.asStateFlow()

    /** Número de artistas da biblioteca usados como semente. */
    private val MAX_SEED_ARTISTS = 5

    /** Quantas tags do perfil agregado são usadas para buscar bandas. */
    private val TOP_TAGS_TO_SEARCH = 6

    fun loadSuggestions() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, suggestions = emptyList(), profileTags = emptyList())
            }

            // ── Passo 1: lê artistas da biblioteca ───────────────────────────
            val allDownloads = downloadRepository.stream().first()
            val libraryArtists: Set<String> = allDownloads
                .mapNotNull { it.artist?.toString()?.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            val seedArtists: List<String> = if (libraryArtists.isEmpty()) {
                // Biblioteca vazia → sementes de bootstrap para novo usuário
                listOf("Iron Maiden", "Metallica", "Black Sabbath", "Slayer", "Pantera")
            } else {
                libraryArtists.shuffled().take(MAX_SEED_ARTISTS)
            }

            _uiState.update { it.copy(seedArtists = seedArtists) }

            // ── Passo 2: busca as tags de cada artista-semente em paralelo ───
            //
            // Cada chamada retorna as tags (gêneros) com contagem de votos do
            // MusicBrainz. Fazemos em paralelo para economizar tempo.
            val tagResults: List<List<TagEntry>> = seedArtists
                .map { artist ->
                    async {
                        // Pequeno delay entre corrotinas para não estourar rate limit
                        musicBrainzService.respectRateLimit()
                        musicBrainzService.getArtistTags(artist)
                    }
                }
                .awaitAll()

            // ── Passo 3: monta o "perfil musical" do usuário ─────────────────
            //
            // Agrega as tags de todos os artistas-semente.
            // Uma tag que aparece em 3 dos seus artistas tem peso 3×.
            // Isso representa o gosto musical real da biblioteca.
            val tagFrequency = mutableMapOf<String, Int>()
            for (tags in tagResults) {
                for (tag in tags) {
                    tagFrequency[tag.name] = (tagFrequency[tag.name] ?: 0) + tag.count
                }
            }

            if (tagFrequency.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Não foi possível obter tags dos artistas.\nVerifique sua internet."
                    )
                }
                return@launch
            }

            // Seleciona as tags mais representativas do perfil
            val profileTags: List<String> = tagFrequency.entries
                .sortedByDescending { it.value }
                .take(TOP_TAGS_TO_SEARCH)
                .map { it.key }

            _uiState.update { it.copy(profileTags = profileTags) }

            // ── Passo 4: busca bandas para cada tag do perfil ────────────────
            //
            // Chamadas sequenciais para respeitar o rate limit de 1 req/seg.
            val libraryNamesLower = libraryArtists.map { it.lowercase() }.toSet()
            val allCandidates = mutableListOf<MBBand>()

            for (tag in profileTags) {
                musicBrainzService.respectRateLimit()
                val bands = musicBrainzService.searchBandsByTag(tag, limit = 20)
                allCandidates.addAll(bands)
            }

            if (allCandidates.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Nenhuma banda encontrada para o seu perfil musical.\nTente recarregar."
                    )
                }
                return@launch
            }

            // ── Passo 5: deduplicação + ranqueamento + filtro de biblioteca ──
            //
            // Para cada banda candidata, calcula quantas das tags do perfil ela
            // compartilha. Bandas com mais tags em comum = mais relevantes.
            // Bandas que já estão na biblioteca são removidas.
            val profileTagSet = profileTags.toSet()
            val seen = mutableSetOf<String>()   // mbid para deduplicação
            val ranked = mutableListOf<RankedBand>()

            for (band in allCandidates) {
                // Remove duplicatas (mesma banda pode aparecer em múltiplas tags)
                if (!seen.add(band.mbid)) continue

                // Remove bandas que o usuário já tem
                if (band.name.lowercase() in libraryNamesLower) continue

                val matchTags = band.tags.filter { it in profileTagSet }
                val matchScore = matchTags.size

                ranked.add(
                    RankedBand(
                        band = band,
                        matchScore = matchScore,
                        matchTags = matchTags
                    )
                )
            }

            // Ordena: mais tags em comum primeiro; empate → ordem original (relevância MusicBrainz)
            val sorted = ranked.sortedByDescending { it.matchScore }.take(40)

            _uiState.update { it.copy(suggestions = sorted, isLoading = false) }
        }
    }

    fun downloadBand(bandName: String) {
        viewModelScope.launch {
            scheduler.schedule(
                url = VideoUrl("ytsearch1:\"$bandName - Best Songs\""),
                path = FilePath(storageResolver.privateDownloadsDir(isAudio = true).absolutePath),
                meta = MediaMetadata(
                    MediaTitle(bandName),
                    ArtistName(bandName),
                    AlbumName("Descoberta Metal")
                ),
                options = DownloadOptions(DownloadType.AUDIO, "m4a", "128")
            )
        }
    }
}
