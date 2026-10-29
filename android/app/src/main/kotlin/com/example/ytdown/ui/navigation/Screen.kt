package com.example.ytdown.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Downloads : Screen("downloads")
    object Library : Screen("library")
    object Browser : Screen("browser")
    object Player : Screen("player")
    object PlaylistSelection : Screen("playlist_selection")
    object Settings : Screen("settings")
    object Equalizer : Screen("equalizer")
    object Diagnostics : Screen("diagnostics")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        /**
         * Nome do grupo vai escapado: "/" ("Big City Nights / Holiday")
         * quebrava o casamento da rota em dois segmentos.
         * (O literal "null" nunca chega aqui: AgrupamentoDaBiblioteca.chave
         * o converte em "Desconhecido" antes — nenhum escape salva o
         * segmento "null", o NavController o rejeita decodificado ou não.)
         */
        fun createRoute(id: String) = "playlist_detail/${Uri.encode(id)}"
    }
    object PlaylistById : Screen("playlist_by_id/{playlistId}") {
        fun createRoute(id: String) = "playlist_by_id/$id"
    }
}
