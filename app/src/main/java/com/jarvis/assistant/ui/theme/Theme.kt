package com.jarvis.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBgPrimary,
    primaryContainer = JarvisCyanDark,
    onPrimaryContainer = JarvisCyanLight,

    secondary = JarvisBlue,
    onSecondary = JarvisTextPrimary,
    secondaryContainer = JarvisBlueDark,
    onSecondaryContainer = JarvisTextPrimary,

    tertiary = JarvisGold,
    onTertiary = JarvisBgPrimary,

    background = JarvisBgPrimary,
    onBackground = JarvisTextPrimary,

    surface = JarvisBgSecondary,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisBgCard,
    onSurfaceVariant = JarvisTextSecondary,

    error = JarvisRed,
    onError = JarvisTextPrimary,

    outline = JarvisBorder,
    outlineVariant = JarvisDivider,

    inverseSurface = JarvisTextPrimary,
    inverseOnSurface = JarvisBgPrimary,
    inversePrimary = JarvisCyanDark,

    surfaceTint = JarvisCyanGlow,
    scrim = JarvisBgPrimary
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = JarvisDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = JarvisBgPrimary.toArgb()
            window.navigationBarColor = JarvisBgPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        content = content
    )
}
