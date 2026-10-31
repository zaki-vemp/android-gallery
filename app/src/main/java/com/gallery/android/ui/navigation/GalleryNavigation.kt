package com.gallery.android.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gallery.android.ui.albums.AlbumsScreen
import com.gallery.android.ui.collage.CollageScreen
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.editor.ImageEditorScreen
import com.gallery.android.ui.favorites.FavoritesScreen
import com.gallery.android.ui.gallery.GalleryScreen
import com.gallery.android.ui.safe.PrivateSafeScreen
import com.gallery.android.ui.search.SearchScreen
import com.gallery.android.ui.settings.SettingsScreen
import com.gallery.android.ui.trash.TrashScreen
import com.gallery.android.ui.viewer.MediaViewerScreen
import kotlinx.coroutines.launch

data class TabSpec(
    val label: String,
    val icon: ImageVector,
)

val homeTabs = listOf(
    TabSpec("Photos", Icons.Outlined.Photo),
    TabSpec("Albums", Icons.Outlined.Folder),
    TabSpec("Search", Icons.Outlined.Search),
    TabSpec("Safe", Icons.Outlined.Lock),
)

@Composable
fun GalleryNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            fadeIn(tween(GalleryMotion.NavEnterMs)) +
                slideInHorizontally(tween(GalleryMotion.NavEnterMs)) { it / 12 } +
                scaleIn(initialScale = 0.98f, animationSpec = tween(GalleryMotion.NavEnterMs))
        },
        exitTransition = {
            fadeOut(tween(GalleryMotion.NavExitMs)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(GalleryMotion.NavExitMs))
        },
        popEnterTransition = {
            fadeIn(tween(GalleryMotion.NavEnterMs)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(GalleryMotion.NavEnterMs))
        },
        popExitTransition = {
            fadeOut(tween(GalleryMotion.NavExitMs)) +
                slideOutHorizontally(tween(GalleryMotion.NavExitMs)) { it / 10 }
        },
    ) {
        composable("home") {
            HomePager(
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") },
                onFavoritesClick = { navController.navigate("favorites") },
                onTrashClick = { navController.navigate("trash") },
                onSettingsClick = { navController.navigate("settings") },
                onAlbumClick = { isUserCreated, id ->
                    if (isUserCreated) {
                        navController.navigate("custom-album/$id")
                    } else {
                        navController.navigate("album/$id")
                    }
                },
                onCollageClick = { navController.navigate("collage") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "viewer/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            enterTransition = {
                fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280))
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                fadeOut(tween(220)) + scaleOut(targetScale = 0.94f, animationSpec = tween(260))
            },
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
            MediaViewerScreen(
                initialMediaId = mediaId,
                onBack = { navController.popBackStack() },
                onEditImage = { uri ->
                    navController.navigate("editor/${Uri.encode(uri.toString())}")
                },
            )
        }
        composable(
            route = "editor/{encodedUri}",
            arguments = listOf(navArgument("encodedUri") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(320),
                ) + fadeIn(tween(280))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(280),
                ) + fadeOut(tween(220))
            },
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("encodedUri") ?: ""
            val uri = Uri.parse(Uri.decode(encoded))
            ImageEditorScreen(
                mediaUri = uri,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onMediaClick = { mediaId -> navController.navigate("viewer/$mediaId") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("trash") {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable("collage") {
            CollageScreen(onBack = { navController.popBackStack() })
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
                onSettingsClick = { navController.navigate("settings") },
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
                onSettingsClick = { navController.navigate("settings") },
            )
        }
    }
}

@Composable
fun HomePager(
    onMediaClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (isUserCreated: Boolean, id: Long) -> Unit,
    onCollageClick: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { homeTabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { homeTabs[it].label },
        ) { page ->
            when (page) {
                0 -> GalleryScreen(
                    onMediaClick = onMediaClick,
                    onFavoritesClick = onFavoritesClick,
                    onTrashClick = onTrashClick,
                    onSettingsClick = onSettingsClick,
                )
                1 -> AlbumsScreen(
                    onAlbumClick = { album ->
                        if (album.isUserCreated) onAlbumClick(true, album.id)
                        else onAlbumClick(false, album.bucketId)
                    },
                    onFavoritesClick = onFavoritesClick,
                    onTrashClick = onTrashClick,
                    onSafeClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    onCollageClick = onCollageClick,
                )
                2 -> SearchScreen(onMediaClick = onMediaClick)
                else -> PrivateSafeScreen(onMediaClick = onMediaClick)
            }
        }
        ApertureBottomBar(
            pagerState = pagerState,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
    }
}

@Composable
fun ApertureBottomBar(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val divider = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedPage = pagerState.currentPage

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f))
            .drawBehind {
                drawLine(divider, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            }
            .navigationBarsPadding(),
    ) {
        homeTabs.forEachIndexed { index, tab ->
            val selected = selectedPage == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .drawBehind {
                        if (selected) {
                            drawLine(
                                accent,
                                Offset(0f, 0f),
                                Offset(size.width, 0f),
                                2.dp.toPx(),
                            )
                        }
                    }
                    .padding(top = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) accent else dim,
                )
                Text(
                    text = tab.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) accent else dim,
                    letterSpacing = 0.12.em,
                )
            }
        }
    }
}
