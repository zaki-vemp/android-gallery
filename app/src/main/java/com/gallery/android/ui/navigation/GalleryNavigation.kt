package com.gallery.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.gallery.android.ui.albums.AlbumsScreen
import com.gallery.android.ui.favorites.FavoritesScreen
import com.gallery.android.ui.gallery.GalleryScreen
import com.gallery.android.ui.safe.PrivateSafeScreen
import com.gallery.android.ui.search.SearchScreen
import com.gallery.android.ui.settings.SettingsScreen
import com.gallery.android.ui.trash.TrashScreen
import com.gallery.android.ui.viewer.MediaViewerScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Photos : Screen("photos", "Photos", Icons.Default.Photo)
    object Albums : Screen("albums", "Albums", Icons.Default.PhotoAlbum)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Safe : Screen("safe", "Safe", Icons.Default.Lock)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Photos, Screen.Albums, Screen.Search, Screen.Safe, Screen.Settings
)

@Composable
fun GalleryNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Photos.route,
        enterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(initialOffsetX = { 100 }) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { 100 }) },
    ) {
        composable(Screen.Photos.route) {
            GalleryScreen(
                onMediaClick = { mediaId ->
                    navController.navigate("viewer/$mediaId")
                },
                onFavoritesClick = { navController.navigate("favorites") },
                onTrashClick = { navController.navigate("trash") },
            )
        }
        composable(Screen.Albums.route) {
            AlbumsScreen(
                onAlbumClick = { album ->
                    if (album.isUserCreated) {
                        navController.navigate("custom-album/${album.id}")
                    } else {
                        navController.navigate("album/${album.bucketId}")
                    }
                },
                onFavoritesClick = { navController.navigate("favorites") },
                onTrashClick = { navController.navigate("trash") },
                onSafeClick = { navController.navigate(Screen.Safe.route) },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") }
            )
        }
        composable(Screen.Safe.route) {
            PrivateSafeScreen(
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(
            route = "viewer/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
            MediaViewerScreen(
                initialMediaId = mediaId,
                onBack = { navController.popBackStack() },
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("trash") {
            TrashScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "album/{bucketId}",
            arguments = listOf(navArgument("bucketId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getLong("bucketId") ?: 0L
            GalleryScreen(
                bucketId = bucketId,
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") },
                onFavoritesClick = { navController.navigate("favorites") },
                onTrashClick = { navController.navigate("trash") },
            )
        }
        composable(
            route = "custom-album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            GalleryScreen(
                customAlbumId = albumId,
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") },
                onFavoritesClick = { navController.navigate("favorites") },
                onTrashClick = { navController.navigate("trash") },
            )
        }
    }
}

@Composable
fun GalleryBottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 12.dp,
    ) {
        bottomNavScreens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.outline,
                    unselectedTextColor = MaterialTheme.colorScheme.outline,
                ),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
