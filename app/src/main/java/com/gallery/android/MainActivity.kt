package com.gallery.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gallery.android.ui.navigation.GalleryNavHost
import com.gallery.android.ui.settings.ThemeMode
import com.gallery.android.ui.theme.GalleryTheme
import com.gallery.android.ui.theme.WireframeDarkBg
import com.gallery.android.ui.theme.WireframeLightBg
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
            val themeModeKey = stringPreferencesKey("theme_mode")
            val darkThemeKey = booleanPreferencesKey("dark_theme")
            val dynamicColorKey = booleanPreferencesKey("dynamic_color")
            val appearanceFlow = remember(dataStore) {
                dataStore.data.map { prefs ->
                    val mode = prefs[themeModeKey] ?: when (prefs[darkThemeKey]) {
                        true -> ThemeMode.DARK
                        false -> ThemeMode.LIGHT
                        null -> ThemeMode.SYSTEM
                    }
                    mode to (prefs[dynamicColorKey] ?: false)
                }
            }
            val appearance by appearanceFlow.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val loadedAppearance = appearance
            if (loadedAppearance == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (systemDark) WireframeDarkBg else WireframeLightBg),
                )
                return@setContent
            }
            val (themeMode, useDynamicColor) = loadedAppearance
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDark
            }

            GalleryTheme(darkTheme = isDarkTheme, dynamicColor = useDynamicColor) {
                GalleryApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GalleryApp(viewModel: MainViewModel = hiltViewModel()) {
    val permissions = remember { PermissionUtils.getRequiredMediaPermissions().toList() }
    val permissionsState = rememberMultiplePermissionsState(permissions)
    var hadPermission by remember { mutableStateOf(permissionsState.allPermissionsGranted) }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        val granted = permissionsState.allPermissionsGranted
        if (!granted) {
            permissionsState.launchMultiplePermissionRequest()
        } else if (!hadPermission) {
            viewModel.onPermissionNewlyGranted()
        }
        hadPermission = granted
    }

    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        GalleryNavHost(navController)
    }
}
