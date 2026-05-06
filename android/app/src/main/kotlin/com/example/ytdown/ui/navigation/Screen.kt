package com.example.ytdown.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Downloads : Screen("downloads")
    object Library : Screen("library")
    object Browser : Screen("browser")
    object Player : Screen("player")
    object PlaylistSelection : Screen("playlist_selection")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(id: String) = "playlist_detail/$id"
    }
    object PlaylistById : Screen("playlist_by_id/{playlistId}") {
        fun createRoute(id: String) = "playlist_by_id/$id"
    }
}
