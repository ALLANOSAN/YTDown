package com.example.ytdown.ui.models

data class GenreUiModel(
    val label: String,
    val value: String, // ex: "45%"
    val color: Long // Compose Color value
)

data class ArtistUiModel(
    val name: String,
    val playCount: String,
    val imageUrl: String?
)

data class ListeningPointUi(
    val x: Float,
    val y: Float,
    val label: String
)

data class MusicProfileUiModel(
    val totalListeningHours: String,
    val favoriteGenres: List<GenreUiModel>,
    val topArtists: List<ArtistUiModel>,
    val listeningTrend: List<ListeningPointUi>,
    val score: Int
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val profile: MusicProfileUiModel
    ) : DashboardUiState

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : DashboardUiState
}