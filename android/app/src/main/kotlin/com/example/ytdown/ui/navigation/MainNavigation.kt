package com.example.ytdown.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.ui.PlayerViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.screens.*
import com.example.ytdown.ui.screens.EqualizerScreen
import com.example.ytdown.ui.screens.MetalDiscoveryScreen
import com.example.ytdown.ui.MetalDiscoveryViewModel

@Composable
fun MainNavigation(
    navController: NavHostController,
    viewModel: DownloadViewModel
) {
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val systemViewModel: SystemViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToPlaylistSelection = { navController.navigate(Screen.PlaylistSelection.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadListScreen(
                viewModel = viewModel,
                onNavigateToBrowser = { navController.navigate(Screen.Browser.route) }
            )
        }
        composable(Screen.Library.route) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = viewModel,
                systemViewModel = systemViewModel,
                playerViewModel = playerViewModel,
                libraryViewModel = libraryViewModel,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToDetail = { id -> navController.navigate(Screen.PlaylistDetail.createRoute(id)) },
                onNavigateToPlaylist = { id -> navController.navigate(Screen.PlaylistById.createRoute(id)) }
            )
        }
        composable(Screen.Browser.route) {
            val context = LocalContext.current
            BrowserScreen(onUrlRequest = { videoUrl ->
                viewModel.onUrlInputChanged(videoUrl.value)
                viewModel.fetchVideoDetails(context, videoUrl)
                navController.navigate(Screen.Home.route)
            })
        }
        composable(Screen.MetalDiscovery.route) {
            val discoveryViewModel: MetalDiscoveryViewModel = hiltViewModel()
            MetalDiscoveryScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Player.route) {
            PlayerFullScreen(
                viewModel = playerViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable(Screen.PlaylistSelection.route) {
            PlaylistSelectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFinish = { navController.navigate(Screen.Downloads.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = systemViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) }
            )
        }
        composable(Screen.Equalizer.route) {
            val equalizerManager = com.example.ytdown.di.HiltEntryPoints.getEqualizerManager(LocalContext.current)
            EqualizerScreen(
                playerViewModel = playerViewModel,
                equalizerManager = equalizerManager
            )
        }
        composable(Screen.Diagnostics.route) {
            DownloadDiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaylistDetailScreen(
                title = playlistId,
                viewModel = viewModel,
                systemViewModel = systemViewModel,
                playerViewModel = playerViewModel,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onBack = { navController.popBackStack() },
                isPlaylistId = false
            )
        }
        composable(
            route = Screen.PlaylistById.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaylistDetailScreen(
                title = playlistId,
                viewModel = viewModel,
                systemViewModel = systemViewModel,
                playerViewModel = playerViewModel,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onBack = { navController.popBackStack() },
                isPlaylistId = true
            )
        }
    }
}
