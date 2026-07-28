package com.attentionos.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalMotionEnabled = staticCompositionLocalOf { true }

private val LightColors = lightColorScheme(
    primary = Forest800,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Forest950,
    secondary = Mint500,
    onSecondary = Forest950,
    secondaryContainer = Color(0xFFE2F4EF),
    onSecondaryContainer = Forest950,
    tertiary = Sun500,
    onTertiary = Forest950,
    background = CanvasLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F3F6),
    onSurfaceVariant = MutedInk,
    outline = OutlineLight,
    outlineVariant = Color(0xFFEDF0F4),
    error = Coral500,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Ice500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF243B63),
    onPrimaryContainer = Color(0xFFE3EDFF),
    secondary = Mint500,
    onSecondary = Forest950,
    secondaryContainer = Color(0xFF173B37),
    onSecondaryContainer = Mint300,
    tertiary = Sun500,
    onTertiary = Forest950,
    background = CanvasDark,
    onBackground = Color(0xFFF4F2FF),
    surface = SurfaceDark,
    onSurface = Color(0xFFF4F2FF),
    surfaceVariant = Color(0xFF202D40),
    onSurfaceVariant = Color(0xFFB8B7C8),
    outline = Color(0xFF35445A),
    outlineVariant = Color(0xFF253247),
    error = Coral500,
    onError = Color.White,
)

private val AttentionShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AttentionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMotionEnabled provides motionEnabled) {
        MaterialTheme(
            colorScheme = colors,
            typography = AttentionTypography,
            shapes = AttentionShapes,
            content = content,
        )
    }
}
