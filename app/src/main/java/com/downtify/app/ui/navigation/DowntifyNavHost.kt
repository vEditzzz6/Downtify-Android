package com.downtify.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.downtify.app.ui.screens.home.HomeScreen
import com.downtify.app.ui.screens.library.LibraryScreen
import com.downtify.app.ui.screens.monitor.MonitorScreen
import com.downtify.app.ui.screens.player.MiniPlayer
import com.downtify.app.ui.screens.player.PlayerScreen
import com.downtify.app.ui.screens.search.SearchScreen
import com.downtify.app.ui.screens.settings.SettingsScreen

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Monitor : Screen("monitor", "Monitor", Icons.Default.Visibility)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Player : Screen("player", "Player", Icons.Default.Headset)
}

@Composable
fun DowntifyNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Library.route,
        Screen.Monitor.route,
        Screen.Settings.route
    )

    var hasPlayingSong by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiniPlayer(
                        navController = navController,
                        onHasSong = { hasPlayingSong = it }
                    )
                    NavigationBar {
                        val items = listOf(
                            Screen.Home,
                            Screen.Search,
                            Screen.Library,
                            Screen.Monitor,
                            Screen.Settings
                        )
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val bottomPadding = if (hasPlayingSong) 114.dp else 56.dp
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(bottom = bottomPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) { HomeScreen(navController) }
                composable(Screen.Search.route) { SearchScreen() }
                composable(Screen.Library.route) { LibraryScreen(navController) }
                composable(Screen.Monitor.route) { MonitorScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
                composable(Screen.Player.route) { PlayerScreen(navController) }
            }
        }
    }
}
