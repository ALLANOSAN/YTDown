package com.example.ytdown.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ytdown.ui.BandDetailsViewModel
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.LibraryViewModel
import com.example.ytdown.core.audio.BassFXEngine
import com.example.ytdown.ui.PlaybackViewModel
import com.example.ytdown.ui.SystemViewModel
import com.example.ytdown.ui.screens.*
import com.example.ytdown.ui.screens.EqualizerScreen
import com.example.ytdown.ui.screens.BandDetailsScreen

@Composable
fun MainNavigation(
    navController: NavHostController,
    viewModel: DownloadViewModel
) {
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val systemViewModel: SystemViewModel = hiltViewModel()
    val bassFXEngine: BassFXEngine = hiltViewModel()

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
                playbackViewModel = playbackViewModel,
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
            val enhancedMetalViewModel: com.example.ytdown.ui.EnhancedMetalViewModel = hiltViewModel()
            com.example.ytdown.ui.screens.metal.EnhancedMetalScreen(
                viewModel = enhancedMetalViewModel,
                onBandClick = { bandName ->
                    navController.navigate(Screen.BandDetails.createRoute(bandName))
                },
                onNavigateToProfile = { }
            )
        }
        composable(
            route = Screen.BandDetails.route,
            arguments = listOf(navArgument("bandName") { type = NavType.StringType })
        ) { backStackEntry ->
            val bandName = backStackEntry.arguments?.getString("bandName") ?: ""
            BandDetailsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Player.route) {
            PlayerFullScreen(
                viewModel = playbackViewModel,
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
            val equalizerViewModel: com.example.ytdown.core.audio.EqualizerViewModel = hiltViewModel()
            EqualizerScreen(
                viewModel = equalizerViewModel
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
                playbackViewModel = playbackViewModel,
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
                playbackViewModel = playbackViewModel,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onBack = { navController.popBackStack() },
                isPlaylistId = true
            )
        }
    }
}