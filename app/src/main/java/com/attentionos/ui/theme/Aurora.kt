package com.attentionos.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The canvas every screen sits on.
 *
 * The single most important change from the previous build, which drew flat white or flat black
 * behind everything. A gradient canvas with soft drifting blooms is what gives the interface
 * depth; the glass surfaces layered on top only read as glass because there is something varied
 * behind them to show through.
 *
 * Motion here is deliberately near-imperceptible — it should register as the screen being alive,
 * not as an animation demanding attention on an app opened dozens of times a day.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val enabled = motionEnabled()
    val transition = rememberInfiniteTransition(label = "aurora")

    // Two blooms drifting on different periods so the canvas never visibly loops.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-drift",
    )
    val counterDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-counter-drift",
    )

    val stops = if (dark) DarkCanvas else LightCanvas
    val violetBloom = if (dark) BloomVioletDark else BloomVioletLight
    val mintBloom = if (dark) BloomMintDark else BloomMintLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(stops))
            .drawBehind {
                // Violet bloom, upper left, drifting down-right.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(violetBloom, Color.Transparent),
                        center = Offset(
                            x = size.width * (0.12f + 0.16f * drift),
                            y = size.height * (0.08f + 0.10f * drift),
                        ),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(
                        x = size.width * (0.12f + 0.16f * drift),
                        y = size.height * (0.08f + 0.10f * drift),
                    ),
                )
                // Mint bloom, lower right, drifting on a slower cycle.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(mintBloom, Color.Transparent),
                        center = Offset(
                            x = size.width * (0.88f - 0.18f * counterDrift),
                            y = size.height * (0.82f - 0.12f * counterDrift),
                        ),
                        radius = size.width * 0.80f,
                    ),
                    radius = size.width * 0.80f,
                    center = Offset(
                        x = size.width * (0.88f - 0.18f * counterDrift),
                        y = size.height * (0.82f - 0.12f * counterDrift),
                    ),
                )
            },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (dark) CanvasContentDark else CanvasContentLight,
            content = { content() },
        )
    }
}

// Canvas gradients. Dark is the primary rendering; light is the same idea at higher luminance
// rather than a separate design.
private val DarkCanvas = listOf(
    Color(0xFF2A1466),
    Color(0xFF1E1050),
    Color(0xFF150C38),
    Color(0xFF0B0720),
)

private val LightCanvas = listOf(
    Color(0xFFEFE9FF),
    Color(0xFFF5F1FF),
    Color(0xFFFBF9FF),
)

private val BloomVioletDark = Color(0xFF7C5CFF).copy(alpha = 0.55f)
private val BloomMintDark = Color(0xFF2AA8FF).copy(alpha = 0.30f)
private val CanvasContentDark = Color(0xFFF4F1FF)
private val CanvasContentLight = Color(0xFF1A1230)
private val BloomVioletLight = Color(0xFF7C5CFF).copy(alpha = 0.16f)
private val BloomMintLight = Color(0xFF38E0C0).copy(alpha = 0.12f)
