package com.attentionos.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Shared Signal Garden canvas.
 *
 * Both themes carry the same barely-visible branching motif. It gives open layouts continuity
 * without placing every section in a card, and drifts slowly enough to register as atmosphere
 * rather than another interruption.
 */
@Composable
fun AppCanvas(
    modifier: Modifier = Modifier,
    dark: Boolean = LocalDarkTheme.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = motionEnabled()
    val transition = rememberInfiniteTransition(label = "signal-canvas")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "branch-drift",
    )
    val base = MaterialTheme.colorScheme.background
    val bottom = if (dark) Color(0xFF041317) else Color(0xFFEFF3EC)
    val branch = if (dark) SignalColors.Mint else SignalColors.MintDark
    val seed = if (dark) SignalColors.Sun else SignalColors.TangerineDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(base, bottom)))
            .drawBehind {
                val shift = size.height * 0.018f * drift
                val stroke = Stroke(
                    width = size.minDimension * 0.0023f,
                    cap = StrokeCap.Round,
                )

                val rightStem = Path().apply {
                    moveTo(size.width * 1.02f, size.height * 0.04f + shift)
                    cubicTo(
                        size.width * 0.78f,
                        size.height * 0.18f + shift,
                        size.width * 0.98f,
                        size.height * 0.34f + shift,
                        size.width * 0.76f,
                        size.height * 0.48f + shift,
                    )
                    cubicTo(
                        size.width * 0.62f,
                        size.height * 0.58f + shift,
                        size.width * 0.84f,
                        size.height * 0.70f + shift,
                        size.width * 0.68f,
                        size.height * 0.82f + shift,
                    )
                }
                drawPath(rightStem, branch.copy(alpha = if (dark) 0.10f else 0.07f), style = stroke)

                listOf(
                    0.18f to -0.09f,
                    0.31f to 0.10f,
                    0.48f to -0.11f,
                    0.67f to 0.10f,
                ).forEach { (y, direction) ->
                    val start = Offset(
                        size.width * (0.87f - y * 0.12f),
                        size.height * y + shift,
                    )
                    val end = Offset(
                        start.x + size.width * direction,
                        start.y - size.height * 0.055f,
                    )
                    drawLine(
                        color = branch.copy(alpha = if (dark) 0.08f else 0.055f),
                        start = start,
                        end = end,
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = seed.copy(alpha = if (dark) 0.20f else 0.12f),
                        radius = size.minDimension * 0.006f,
                        center = end,
                    )
                }
            },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = { content() },
        )
    }
}
