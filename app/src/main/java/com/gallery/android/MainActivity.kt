package com.gallery.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gallery.android.ui.navigation.GalleryBottomNavBar
import com.gallery.android.ui.navigation.GalleryNavHost
import com.gallery.android.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.gallery.android.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
            val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
            val isDarkTheme by dataStore.data.map { it[DARK_THEME_KEY] ?: false }
                .collectAsStateWithLifecycle(isSystemInDarkTheme())
            val isDynamic by dataStore.data.map { it[DYNAMIC_COLOR_KEY] ?: true }
                .collectAsStateWithLifecycle(true)

            GalleryTheme(darkTheme = isDarkTheme, dynamicColor = isDynamic) {
                GalleryApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GalleryApp() {
    val permissions = remember { PermissionUtils.getRequiredMediaPermissions().toList() }
    val permissionsState = rememberMultiplePermissionsState(permissions)

    if (!permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) { permissionsState.launchMultiplePermissionRequest() }
    }

    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { GalleryBottomNavBar(navController) },
    ) { _ ->
        GalleryNavHost(navController)
    }
}
