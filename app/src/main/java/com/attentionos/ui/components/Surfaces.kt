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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Elevation
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled

/**
 * Loading placeholder.
 *
 * A shape where content will land beats a spinner: the layout does not jump when data arrives.
 */
@Composable
internal fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = Spacing.huge,
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
internal fun VSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

/** Horizontal spacer on the spacing scale. */
@Composable
internal fun HSpace(width: Dp) {
    Spacer(Modifier.width(width))
}

@Preview(name = "Surfaces · light")
@Composable
private fun SurfacesPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.lg)) {
                SkeletonBlock()
                VSpace(Spacing.md)
                SkeletonBlock(height = 48.dp)
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
                SkeletonBlock()
                VSpace(Spacing.md)
                SkeletonBlock(height = 48.dp)
            }
        }
    }
}
