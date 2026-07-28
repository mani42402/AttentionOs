package com.attentionos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Elevation
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled

/**
 * The app's standard content container.
 *
 * Uses surface *tone* for depth rather than a border. The old UI drew a 1dp outlineVariant
 * stroke around roughly fifteen separate cards by hand, which is both repetitive and heavier
 * looking than the tonal system Material 3 provides.
 */
@Composable
internal fun AttentionCard(
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = Radius.card,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = tone,
        tonalElevation = Elevation.flat,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Section heading.
 *
 * Marked as a heading for screen readers, which no header in the old UI was — so there was no
 * way to navigate the app by structure.
 */
@Composable
internal fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Small uppercase label that introduces a group. */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * A coloured status dot.
 *
 * Hidden from accessibility: it never carries meaning on its own, always sitting beside text
 * that says the same thing. Announcing it would just add noise.
 */
@Composable
internal fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Spacing.sm,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .size(size)
            .background(color, RoundedCornerShape(percent = 50)),
    )
}

/**
 * Placeholder block for content that has not loaded.
 *
 * The old app rendered literally nothing while loading — a blank Surface on cold start — so the
 * first thing a user saw was an empty screen with no indication anything was happening.
 */
@Composable
internal fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = Spacing.huge,
    shape: Shape = Radius.card,
) {
    val enabled = motionEnabled()
    val shimmer by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = Motion.gentle(enabled),
        label = "skeleton",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(shimmer, shape)
            .clearAndSetSemantics { },
    )
}

/** Vertical spacer on the spacing scale. */
@Composable
internal fun VSpace(height: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(height))
}

/** Horizontal spacer on the spacing scale. */
@Composable
internal fun HSpace(width: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.width(width))
}

@Preview(name = "Surfaces · light")
@Composable
private fun SurfacesPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.lg)) {
                GroupLabel("Today")
                AttentionCard {
                    SectionHeading("Recent notifications")
                    VSpace(Spacing.md)
                    SkeletonBlock()
                }
            }
        }
    }
}

@Preview(name = "Surfaces · dark")
@Composable
private fun SurfacesDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.lg)) {
                GroupLabel("Today")
                AttentionCard {
                    SectionHeading("Recent notifications")
                    VSpace(Spacing.md)
                    SkeletonBlock()
                }
            }
        }
    }
}
