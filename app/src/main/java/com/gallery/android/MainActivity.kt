package com.gallery.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gallery.android.ui.navigation.GalleryNavHost
import com.gallery.android.ui.theme.GalleryTheme
import com.gallery.android.utils.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkThemeKey = booleanPreferencesKey("dark_theme")
            val darkThemeFlow = remember(dataStore) {
                dataStore.data.map { it[darkThemeKey] ?: true }
            }
            val isDarkTheme by darkThemeFlow.collectAsStateWithLifecycle(true)

            GalleryTheme(darkTheme = isDarkTheme) {
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        GalleryNavHost(navController)
    }
}
