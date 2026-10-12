package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = KemCyanSecondary,
    onPrimary = KemCyanOnSecondary,
    primaryContainer = KemTealPrimary,
    onPrimaryContainer = KemTealOnPrimary,
    secondary = KemCyanSecondary,
    onSecondary = KemCyanOnSecondary,
    secondaryContainer = KemCyanContainer,
    onSecondaryContainer = KemCyanOnContainer,
    tertiary = KemTertiaryContainer,
    onTertiary = KemOnTertiaryContainer,
    background = KemDarkBackground,
    onBackground = KemDarkOnBackground,
    surface = KemDarkSurface,
    onSurface = KemDarkOnSurface,
    surfaceVariant = KemDarkSurfaceVariant,
    onSurfaceVariant = KemDarkOnSurfaceVariant,
    outline = KemDarkOutline,
    error = KemError,
    onError = KemTealOnPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = KemTealPrimary,
    onPrimary = KemTealOnPrimary,
    primaryContainer = KemTealContainer,
    onPrimaryContainer = KemTealOnContainer,
    secondary = KemCyanSecondary,
    onSecondary = KemCyanOnSecondary,
    secondaryContainer = KemCyanContainer,
    onSecondaryContainer = KemCyanOnContainer,
    tertiary = KemTertiary,
    onTertiary = KemOnTertiary,
    background = KemLightBackground,
    onBackground = KemLightOnBackground,
    surface = KemLightSurface,
    onSurface = KemLightOnSurface,
    surfaceVariant = KemLightSurfaceVariant,
    onSurfaceVariant = KemLightOnSurfaceVariant,
    outline = KemLightOutline,
    error = KemError,
    onError = KemTealOnPrimary
)

@Composable
fun KemSkenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for template compatibility if needed
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = KemSkenTheme(darkTheme = darkTheme, content = content)
