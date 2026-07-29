package com.attentionos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.attentionos.R
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode

/** Material's minimum accessible touch target. */
private val MinTouchTarget = 48.dp

@Composable
internal fun AttentionBrand(
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    color: Color = SignalColors.Tangerine,
    nameColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Fractions rather than Offsets: the list is allocated once instead of on every frame.
        val petals = remember { listOf(0.32f to 0.32f, 0.68f to 0.32f, 0.32f to 0.68f, 0.68f to 0.68f) }
        Canvas(Modifier.size(28.dp)) {
            val r = size.minDimension * 0.22f
            petals.forEach { (x, y) ->
                drawCircle(color, r, Offset(size.width * x, size.height * y))
            }
        }
        if (showName) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = nameColor,
            )
        }
    }
}

@Composable
internal fun OnDeviceBadge(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(Radius.pill)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = Radius.pill,
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
            drawRoundRect(
                color = primary,
                topLeft = Offset(size.width * 0.2f, size.height * 0.38f),
                size = Size(size.width * 0.6f, size.height * 0.5f),
                cornerRadius = CornerRadius(size.width * 0.12f),
                style = stroke,
            )
            val shackle = Path().apply {
                moveTo(size.width * 0.34f, size.height * 0.4f)
                cubicTo(
                    size.width * 0.34f,
                    size.height * 0.08f,
                    size.width * 0.66f,
                    size.height * 0.08f,
                    size.width * 0.66f,
                    size.height * 0.4f,
                )
            }
            drawPath(shackle, primary, style = stroke)
        }
        Text(
            stringResource(R.string.common_on_device),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SignalCard(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(fill)
            .border(1.dp, border, Radius.card)
            .then(interaction)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The one inverted surface per screen: always ink, in both themes.
 *
 * It provides its own content colour. Callers must not restate it on every [Text] — a single
 * omission would otherwise render ink-on-ink in light mode, and the failure only shows up on
 * the theme the author wasn't looking at.
 */
@Composable
internal fun SignalFeatureSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.hero)
            .background(SignalColors.InkRaised)
            .border(1.dp, SignalColors.InkBorder, Radius.hero)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides SignalColors.Cream,
            content = { content() },
        )
    }
}

/** Secondary text on a [SignalFeatureSurface], where `onSurfaceVariant` would be wrong. */
internal val FeatureSurfaceMutedColor: Color
    @Composable get() = SignalColors.CreamMuted

@Composable
internal fun SignalScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            VSpace(Spacing.xs)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
internal fun SignalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
            )
        }
    }
}

@Composable
internal fun SignalEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SignalDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(modifier.size(size).background(color, CircleShape))
}

@Composable
private fun SignalGallery() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttentionBrand(modifier = Modifier.weight(1f))
                OnDeviceBadge()
            }
            SignalScreenHeader(
                title = "Summary",
                subtitle = "What your helper handled and how safely it is learning.",
            )
            SignalFeatureSurface {
                Column {
                    SignalEyebrow("Today at a glance", color = FeatureSurfaceMutedColor)
                    VSpace(Spacing.sm)
                    Text("4 checked", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "The one inverted surface per screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeatureSurfaceMutedColor,
                    )
                }
            }
            SignalSectionHeader(title = "Recent decisions", action = "See all", onAction = {})
            SignalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalDot(SignalColors.Mint)
                    HSpace(Spacing.sm)
                    Text("Standard container", style = MaterialTheme.typography.bodyMedium)
                }
            }
            SignalCard(onClick = {}) {
                Text("Tappable container", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Signal components · light", heightDp = 700)
@Composable
private fun SignalComponentsPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) { SignalGallery() }
}

@Preview(name = "Signal components · dark", heightDp = 700)
@Composable
private fun SignalComponentsDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) { SignalGallery() }
}

@Preview(name = "Signal components · RTL", heightDp = 700, locale = "ar")
@Composable
private fun SignalComponentsRtlPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) { SignalGallery() }
}
