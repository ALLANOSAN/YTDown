package com.example.ytdown.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Downloads : Screen("downloads")
    object Library : Screen("library")
    object Browser : Screen("browser")
    object Player : Screen("player")
    object MetalDiscovery : Screen("metal_discovery")
    object PlaylistSelection : Screen("playlist_selection")
    object Settings : Screen("settings")
    object Equalizer : Screen("equalizer")
    object Diagnostics : Screen("diagnostics")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(id: String) = "playlist_detail/$id"
    }
    object PlaylistById : Screen("playlist_by_id/{playlistId}") {
        fun createRoute(id: String) = "playlist_by_id/$id"
    }
    
    // Novas rotas para descoberta de Metal
    object BandDetails : Screen("band_details/{bandName}") {
        fun createRoute(bandName: String) = "band_details/${bandName.replace("/", "_")}"
    }
    object AlbumDownload : Screen("album_download/{bandName}/{albumName}") {
        fun createRoute(bandName: String, albumName: String) = 
            "album_download/${bandName.replace("/", "_")}/${albumName.replace("/", "_")}"
    }
}
