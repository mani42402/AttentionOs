package com.attentionos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * How the app decides between light and dark.
 *
 * The previous build offered only a boolean that defaulted to light, so a user with the system
 * in dark mode got a bright app until they found the setting — the app ignored a preference the
 * OS had already collected.
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}

/**
 * Whether the app is currently rendering dark.
 *
 * Components must read this rather than [isSystemInDarkTheme], or a user who overrides the
 * system setting gets a light card on a dark canvas.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AttentionTheme(
    themeMode: ThemeMode = ThemeMode.System,
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // No Material You option. It only half-applied: the wallpaper palette took over the
    // colour scheme while the flow lanes and brand mark kept their own pigments, so the result
    // was a mixture rather than a switch. One palette is the more honest answer.
    val colorScheme: ColorScheme = if (darkTheme) AttentionDarkColors else AttentionLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // True edge-to-edge. The old build called enableEdgeToEdge(), forced light status
            // icons regardless of theme, then painted an opaque navy strip over the status bar
            // on every screen to keep them legible — which put a dark slab above light content
            // and defeated the point. Content now draws under the bars and the icon colour
            // simply follows the theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalMotionEnabled provides motionEnabled,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AttentionTypography,
            shapes = AttentionShapes,
            content = content,
        )
    }
}
