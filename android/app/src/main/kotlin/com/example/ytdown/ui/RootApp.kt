package com.example.ytdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ytdown.ui.components.MiniPlayer
import com.example.ytdown.ui.navigation.MainNavigation
import com.example.ytdown.ui.navigation.Screen
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.theme.YTDownPurple
@Composable
fun RootApp(viewModel: DownloadViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val systemViewModel: SystemViewModel = hiltViewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                // Oculta mini player e bottom nav em telas que precisam de espaço total
                val hideNav = currentRoute == Screen.Player.route || 
                              currentRoute == Screen.Equalizer.route
                if (!hideNav) {
                    Column {
                        MiniPlayer(
                                viewModel = playbackViewModel,
                                onClick = { navController.navigate(Screen.Player.route) }
                        )
                        RootBottomNavigation(currentRoute) { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            MainNavigation(
                navController = navController,
                viewModel = viewModel,
                playbackViewModel = playbackViewModel,
                systemViewModel = systemViewModel
            )
        }
    }
}

@Composable
private fun RootBottomNavigation(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        val items =
                listOf(
                        NavigationItem("Buscar", Screen.Home.route, Icons.Filled.Search),
                        NavigationItem("Downloads", Screen.Downloads.route, Icons.Filled.Folder),
                        NavigationItem("Biblioteca", Screen.Library.route, Icons.Filled.MusicNote),
                        NavigationItem("Navegador", Screen.Browser.route, Icons.Filled.Explore)
                )

        items.forEach { item ->
            NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label, fontSize = 10.sp) },
                    colors =
                            NavigationBarItemDefaults.colors(
                                    selectedIconColor = YTDownPurple,
                                    selectedTextColor = YTDownPurple,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                            )
            )
        }
    }
}

private data class NavigationItem(
        val label: String,
        val route: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
)