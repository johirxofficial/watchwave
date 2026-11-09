package com.watchwave.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watchwave.app.AppContainer
import com.watchwave.app.ui.screens.AdvancedSettingsScreen
import com.watchwave.app.ui.screens.CreatePartyScreen
import com.watchwave.app.ui.screens.DiagnosticsScreen
import com.watchwave.app.ui.screens.HistoryScreen
import com.watchwave.app.ui.screens.HomeScreen
import com.watchwave.app.ui.screens.HostScreen
import com.watchwave.app.ui.screens.JoinScreen
import com.watchwave.app.ui.screens.LibraryScreen
import com.watchwave.app.ui.screens.SettingsScreen
import com.watchwave.app.ui.screens.TunnelScreen
import com.watchwave.app.ui.screens.ViewerScreen
import com.watchwave.app.ui.theme.Ink
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveCyan

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination("home", "Home", Icons.Filled.Home),
    BottomDestination("library", "Library", Icons.Filled.VideoLibrary),
    BottomDestination("history", "History", Icons.Filled.History),
    BottomDestination("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun WatchWaveApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf("home", "library", "history", "settings")
    val pendingDeepLink by container.pendingDeepLink.collectAsStateWithLifecycle()

    LaunchedEffect(pendingDeepLink) {
        val consumed = container.consumeDeepLink() ?: return@LaunchedEffect
        navController.navigate("join?link=${Uri.encode(consumed)}")
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color(0xFF0A0F18),
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WaveCyan,
                                selectedTextColor = WaveCyan,
                                indicatorColor = WaveCyan.copy(alpha = 0.14f),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    container = container,
                    onHost = { navController.navigate("library") },
                    onJoin = { navController.navigate("join") },
                    onResumeHost = { navController.navigate("host") },
                    onResumeViewer = { navController.navigate("viewer") },
                    onHistory = { navController.navigate("history") },
                    onTunnel = { navController.navigate("tunnel") },
                    onDiagnostics = { navController.navigate("diagnostics") },
                )
            }

            composable("library") {
                LibraryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onSelect = { uri -> navController.navigate("create?uri=${Uri.encode(uri)}") },
                )
            }

            composable(
                route = "create?uri={uri}",
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                CreatePartyScreen(
                    container = container,
                    videoUri = entry.arguments?.getString("uri").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onStarted = {
                        navController.navigate("host") {
                            popUpTo("home")
                        }
                    },
                    onOpenTunnel = { navController.navigate("tunnel") },
                )
            }

            composable("host") {
                HostScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onEnded = { goHome(navController) },
                    onOpenTunnel = { navController.navigate("tunnel") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }

            composable(
                route = "join?link={link}",
                arguments = listOf(
                    navArgument("link") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                JoinScreen(
                    container = container,
                    initialLink = entry.arguments?.getString("link").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onJoined = {
                        navController.navigate("viewer") {
                            popUpTo("home")
                        }
                    },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }

            composable("viewer") {
                ViewerScreen(
                    container = container,
                    onBack = { goHome(navController) },
                    onLeft = { goHome(navController) },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }

            composable("settings") {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenTunnel = { navController.navigate("tunnel") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenAdvanced = { navController.navigate("advanced-settings") },
                )
            }

            composable("advanced-settings") {
                AdvancedSettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }

            composable("tunnel") {
                TunnelScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                )
            }

            composable("diagnostics") {
                DiagnosticsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("history") {
                HistoryScreen(container = container)
            }
        }
    }
}

private fun goHome(navController: NavHostController) {
    val popped = navController.popBackStack("home", inclusive = false)
    if (!popped) {
        navController.navigate("home") {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
        }
    }
}
