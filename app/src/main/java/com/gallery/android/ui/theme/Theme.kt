package com.gallery.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WireframeDarkColorScheme = darkColorScheme(
    primary = WireframeDarkAccent,
    onPrimary = Color.Black,
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
    val colorScheme = if (darkTheme) WireframeDarkColorScheme else WireframeLightColorScheme

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
