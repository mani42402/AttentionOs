package com.attentionos.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled

/**
 * Empty, loading and error states.
 *
 * The old build had none of these: cold start rendered a blank Surface, an empty list rendered a
 * bare icon, and a failed on-device test printed red text with no way to retry. Empty states are
 * where a user decides whether an app is finished or abandoned.
 */

/** Shown when a list has nothing in it *and* that is a good thing. */
@Composable
internal fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { CalmMark() },
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        illustration()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * Something went wrong, with a way out.
 *
 * The previous failure path was a red string and nothing else; a user could only retry by
 * guessing that pressing the original button again would work.
 */
@Composable
internal fun ErrorState(
    title: String,
    description: String,
    onRetry: () -> Unit,
    retryLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(retryLabel) }
    }
}

/** Skeleton list shown on cold start, replacing the blank screen. */
@Composable
internal fun LoadingState(modifier: Modifier = Modifier, rows: Int = 3) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBlock(height = 120.dp)
        repeat(rows) { SkeletonBlock(height = 72.dp) }
    }
}

/**
 * A quiet breathing ring, used as the "nothing to do" illustration.
 *
 * Drawn on Canvas rather than shipped as an asset: one shape, no dependency, and it scales
 * cleanly at any density. The animation respects the motion preference.
 */
@Composable
internal fun CalmMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    val enabled = motionEnabled()
    val transition = rememberInfiniteTransition(label = "calm-mark")
    val breath by transition.animateFloat(
        initialValue = if (enabled) 0.86f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "calm-breath",
    )

    Canvas(
        modifier = modifier
            .size(72.dp)
            .clearAndSetSemantics { },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius * breath,
        )
        drawCircle(
            color = color,
            radius = radius * 0.58f * breath,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Preview(name = "States · light")
@Composable
private fun StatesPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Column {
            EmptyState(
                title = "All caught up",
                description = "New attention decisions will appear here as notifications arrive.",
                action = { Button(onClick = {}) { Text("Open settings") } },
            )
        }
    }
}

@Preview(name = "States · dark")
@Composable
private fun StatesDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Column {
            ErrorState(
                title = "Could not run the check",
                description = "The on-device model did not respond.",
                onRetry = {},
                retryLabel = "Try again",
            )
        }
    }
}
