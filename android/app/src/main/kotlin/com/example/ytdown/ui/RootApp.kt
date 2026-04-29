package com.example.ytdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ytdown.ui.navigation.MainNavigation
import com.example.ytdown.ui.navigation.Screen
import com.example.ytdown.ui.screens.TagEditorDialog
import com.example.ytdown.ui.components.MiniPlayer
import com.example.ytdown.ui.theme.YTDownPurple
import com.example.ytdown.ui.theme.TextSecondary
import com.example.ytdown.ui.DownloadViewModel
import com.example.ytdown.ui.PlayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RootApp(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val state by viewModel.inputState.collectAsState()

    if (state.showDialog && !state.isPlaylist) {
        TagEditorDialog(
            viewModel = viewModel,
            onConfirm = { folder -> viewModel.startDownloadFlow(folder) }
        )
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute != Screen.Player.route) {
                Column {
                    MiniPlayer(
                        viewModel = playerViewModel,
                        onClick = { navController.navigate(Screen.Player.route) }
                    )
                    RootBottomNavigation(currentRoute) { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).background(Color.Black)) {
            MainNavigation(navController, viewModel)
        }
    }
}

@Composable
private fun RootBottomNavigation(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = Color.Black,
        tonalElevation = 0.dp
    ) {
        val items = listOf(
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
                colors = NavigationBarItemDefaults.colors(
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

private data class NavigationItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
