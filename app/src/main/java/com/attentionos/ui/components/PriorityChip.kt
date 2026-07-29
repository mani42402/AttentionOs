package com.attentionos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.domain.AttentionPriority
import com.attentionos.R
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.LocalDarkTheme
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode

/**
 * The priority badge.
 *
 * Colour and text always agree, so the meaning survives both colour blindness and a greyscale
 * screenshot — the old pill relied on the colour alone to distinguish "high" from "medium".
 */
@Composable
internal fun PriorityChip(
    priority: AttentionPriority,
    modifier: Modifier = Modifier,
) {
    val accent = priority.accent()
    val content = if (LocalDarkTheme.current) {
        accent
    } else {
        when (priority) {
            AttentionPriority.CRITICAL -> SignalColors.CriticalDark
            AttentionPriority.HIGH -> SignalColors.TangerineDark
            AttentionPriority.MEDIUM -> SignalColors.SunDark
            AttentionPriority.LOW -> SignalColors.MintDark
            AttentionPriority.SILENT -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val spoken = priority.describe()
    Row(
        modifier = modifier
            .background(content.copy(alpha = 0.12f), Radius.pill)
            .padding(horizontal = Spacing.md, vertical = 5.dp)
            .semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
    ) {
        SignalDot(color = content, size = 7.dp)
        Text(
            text = priority.label(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Accent for a priority, from the shared semantic palette. */
internal fun AttentionPriority.accent(): Color = when (this) {
    AttentionPriority.CRITICAL -> PriorityColors.critical
    AttentionPriority.HIGH -> PriorityColors.high
    AttentionPriority.MEDIUM -> PriorityColors.medium
    AttentionPriority.LOW -> PriorityColors.low
    AttentionPriority.SILENT -> PriorityColors.silent
}

/** Accent for a stored priority name, for rows that only have the persisted string. */
internal fun accentForPriorityName(name: String): Color =
    runCatching { AttentionPriority.valueOf(name).accent() }
        .getOrDefault(PriorityColors.silent)

/** Short label shown in the chip. */
@Composable
@ReadOnlyComposable
internal fun AttentionPriority.label(): String = when (this) {
    AttentionPriority.CRITICAL -> stringResource(R.string.priority_urgent)
    AttentionPriority.HIGH -> stringResource(R.string.priority_important)
    AttentionPriority.MEDIUM -> stringResource(R.string.priority_normal)
    AttentionPriority.LOW -> stringResource(R.string.priority_can_wait)
    AttentionPriority.SILENT -> stringResource(R.string.priority_quiet)
}

/** Full sentence for screen readers, since the chip's own text is abbreviated. */
@Composable
@ReadOnlyComposable
internal fun AttentionPriority.describe(): String = when (this) {
    AttentionPriority.CRITICAL -> stringResource(R.string.priority_urgent_always_reaches_you)
    AttentionPriority.HIGH -> stringResource(R.string.priority_important_reaches_you_promptly)
    AttentionPriority.MEDIUM -> stringResource(R.string.priority_normal_priority)
    AttentionPriority.LOW -> stringResource(R.string.priority_can_wait_delivered_quietly)
    AttentionPriority.SILENT -> stringResource(R.string.priority_quiet_no_sound_or_vibration)
}

@Preview(name = "Priority chips · light")
@Composable
private fun PriorityChipPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AttentionPriority.entries.forEach { PriorityChip(it) }
        }
    }
}

@Preview(name = "Priority chips · dark")
@Composable
private fun PriorityChipDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AttentionPriority.entries.forEach { PriorityChip(it) }
        }
    }
}
