package com.attentionos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

@Composable
fun AttentionTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        // Material You: let the interface adopt the user's wallpaper palette when they ask for
        // it. Off by default, because the brand identity is part of what makes the app feel
        // like a considered product rather than a system utility.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> AttentionDarkColors
        else -> AttentionLightColors
    }

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

    CompositionLocalProvider(LocalMotionEnabled provides motionEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AttentionTypography,
            shapes = AttentionShapes,
            content = content,
        )
    }
}
