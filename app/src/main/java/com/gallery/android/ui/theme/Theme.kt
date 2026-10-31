package com.gallery.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WireframeDarkColorScheme = darkColorScheme(
    primary = WireframeDarkAccent,
    onPrimary = Color(0xFF062028),
    primaryContainer = WireframeDarkChip,
    onPrimaryContainer = WireframeDarkAccent,
    secondary = WireframeDarkDim,
    onSecondary = WireframeDarkText,
    surface = WireframeDarkSurface,
    background = WireframeDarkBg,
    onBackground = WireframeDarkText,
    onSurface = WireframeDarkText,
    surfaceVariant = WireframeDarkSurface,
    onSurfaceVariant = WireframeDarkDim,
    outline = WireframeDarkLine,
    outlineVariant = WireframeDarkLine,
)

private val WireframeLightColorScheme = lightColorScheme(
    primary = WireframeLightAccent,
    onPrimary = Color.White,
    primaryContainer = WireframeLightChip,
    onPrimaryContainer = WireframeLightAccent,
    secondary = WireframeLightDim,
    onSecondary = WireframeLightText,
    surface = WireframeLightSurface,
    background = WireframeLightBg,
    onBackground = WireframeLightText,
    onSurface = WireframeLightText,
    surfaceVariant = WireframeLightSurface,
    onSurfaceVariant = WireframeLightDim,
    outline = WireframeLightLine,
    outlineVariant = WireframeLightLine,
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> WireframeDarkColorScheme
        else -> WireframeLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GalleryShapes,
        content = content,
    )
}
