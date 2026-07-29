package com.attentionos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.attentionos.ui.theme.Radius

/**
 * Translucent surfaces that sit on the aurora canvas.
 *
 * Depth comes from a hairline border catching light along the top edge, not from a drop shadow.
 * On a gradient canvas a shadow reads as dirt, whereas a lit edge reads as a pane of glass — the
 * effect every reference design uses.
 *
 * Real backdrop blur needs `RenderEffect` (API 31+) and costs a full-screen readback each frame.
 * A calibrated translucent fill over a varied canvas gets close enough that the difference is not
 * visible at these sizes, and it works on every supported device rather than only recent ones.
 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.card,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) GlassFillDark else GlassFillLight)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (dark) {
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.45f))
                    },
                ),
                shape = shape,
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (dark) GlassContentDark else GlassContentLight,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * The one card per screen that is allowed to be loud.
 *
 * A gradient fill in the accent family with a diagonal light streak running corner to corner,
 * taken straight from the weather references. Reserved for hero content — the day's headline
 * figure, the notification being judged — so that it keeps its weight.
 */
@Composable
internal fun FeatureCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.hero,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    colors: List<Color> = listOf(Color(0xFF6D4AFF), Color(0xFF4B2FD6)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors)),
    ) {
        // Diagonal sheen. Offsets are deliberately asymmetric so it reads as a light source
        // rather than a centred highlight.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f),
                        ),
                        start = Offset.Zero,
                        end = Offset(x = 900f, y = 700f),
                    ),
                ),
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A small glass square for the bento rows. */
@Composable
internal fun BentoTile(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier,
        shape = Radius.tile,
        contentPadding = PaddingValues(16.dp),
        dark = dark,
        content = content,
    )
}

private val GlassFillDark = Color.White.copy(alpha = 0.08f)
private val GlassFillLight = Color.White.copy(alpha = 0.62f)
private val GlassContentDark = Color(0xFFF4F1FF)
private val GlassContentLight = Color(0xFF1A1230)
