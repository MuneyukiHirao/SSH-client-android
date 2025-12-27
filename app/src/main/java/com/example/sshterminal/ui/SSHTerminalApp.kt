package com.example.sshterminal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sshterminal.ui.screens.HostEditScreen
import com.example.sshterminal.ui.screens.HostListScreen
import com.example.sshterminal.ui.screens.KeyManagerScreen
import com.example.sshterminal.ui.screens.MultiSessionTerminalScreen
import com.example.sshterminal.ui.screens.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object HostList : Screen("hosts", "Hosts", Icons.Default.Computer)
    object KeyManager : Screen("keys", "Keys", Icons.Default.Key)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Terminal : Screen("terminal/{hostId}", "Terminal", Icons.Default.Computer) {
        fun createRoute(hostId: Long) = "terminal/$hostId"
    }
    object HostEdit : Screen("host/edit?hostId={hostId}", "Edit Host", Icons.Default.Computer) {
        fun createRoute(hostId: Long? = null) = if (hostId != null) "host/edit?hostId=$hostId" else "host/edit"
    }
}

val bottomNavItems = listOf(
    Screen.HostList,
    Screen.KeyManager,
    Screen.Settings
)

@Composable
fun SSHTerminalApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar on terminal screen
    val showBottomBar = currentDestination?.route?.startsWith("terminal") != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HostList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.HostList.route) {
                HostListScreen(
                    onHostClick = { host ->
                        navController.navigate(Screen.Terminal.createRoute(host.id))
                    },
                    onAddHost = {
                        navController.navigate(Screen.HostEdit.createRoute())
                    },
                    onEditHost = { host ->
                        navController.navigate(Screen.HostEdit.createRoute(host.id))
                    }
                )
            }

            composable(Screen.KeyManager.route) {
                KeyManagerScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = Screen.Terminal.route,
                arguments = listOf(
                    navArgument("hostId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val hostId = backStackEntry.arguments?.getLong("hostId") ?: return@composable
                MultiSessionTerminalScreen(
                    initialHostId = hostId,
                    onExit = {
                        navController.popBackStack()
                    },
                    onAddSession = {
                        // Navigate back to host list to select another host
                        navController.navigate(Screen.HostList.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.HostEdit.route,
                arguments = listOf(
                    navArgument("hostId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val hostId = backStackEntry.arguments?.getString("hostId")?.toLongOrNull()
                HostEditScreen(
                    hostId = hostId,
                    onSaved = {
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
