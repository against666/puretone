package com.puretone.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.puretone.player.PureToneApplication
import com.puretone.player.data.prefs.ThemeMode
import com.puretone.player.ui.components.MiniPlayer
import com.puretone.player.ui.library.LibraryScreen
import com.puretone.player.ui.player.PlayerScreen
import com.puretone.player.ui.playlist.AddSongsScreen
import com.puretone.player.ui.playlist.PlaylistDetailScreen
import com.puretone.player.ui.search.SearchScreen
import com.puretone.player.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PLAYER = "player"

    const val PLAYLIST = "playlist/{playlistId}"
    const val ADD_SONGS = "playlist/{playlistId}/add"

    fun playlist(id: Long) = "playlist/$id"

    fun addSongs(id: Long) = "playlist/$id/add"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val Tabs = listOf(
    Tab(Routes.LIBRARY, "音乐库", Icons.Filled.List),
    Tab(Routes.SEARCH, "搜索", Icons.Filled.Search),
    Tab(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun PureToneApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val app = LocalContext.current.applicationContext as PureToneApplication
    val playerState by app.player.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isTab = Tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }
    // 播放页全屏，其余页面只要有在播的歌就常驻迷你条
    val showMiniPlayer = currentRoute != Routes.PLAYER && playerState.hasMedia

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        title = playerState.title,
                        artist = playerState.artist,
                        coverUri = playerState.coverUri,
                        isPlaying = playerState.isPlaying,
                        progress = if (playerState.durationMs > 0L) {
                            playerState.positionMs.toFloat() / playerState.durationMs
                        } else {
                            0f
                        },
                        onClick = { navController.navigate(Routes.PLAYER) },
                        onTogglePlay = app.player::togglePlayPause,
                    )
                }
                if (isTab) {
                    NavigationBar {
                        Tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                )
            }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("playlistId") ?: -1L
                PlaylistDetailScreen(
                    playlistId = id,
                    onBack = { navController.popBackStack() },
                    onAddSongs = { navController.navigate(Routes.addSongs(id)) },
                )
            }

            composable(
                route = Routes.ADD_SONGS,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("playlistId") ?: -1L
                AddSongsScreen(
                    playlistId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
