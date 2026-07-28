package com.attentionos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.domain.AttentionPriority
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
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
    Row(
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), Radius.pill)
            .padding(horizontal = Spacing.md, vertical = 5.dp)
            .semantics { contentDescription = priority.describe() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
    ) {
        StatusDot(color = accent, size = 7.dp)
        Text(
            text = priority.label(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
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
internal fun AttentionPriority.label(): String = when (this) {
    AttentionPriority.CRITICAL -> "Urgent"
    AttentionPriority.HIGH -> "Important"
    AttentionPriority.MEDIUM -> "Normal"
    AttentionPriority.LOW -> "Can wait"
    AttentionPriority.SILENT -> "Quiet"
}

/** Full sentence for screen readers, since the chip's own text is abbreviated. */
internal fun AttentionPriority.describe(): String = when (this) {
    AttentionPriority.CRITICAL -> "Urgent. Always reaches you."
    AttentionPriority.HIGH -> "Important. Reaches you promptly."
    AttentionPriority.MEDIUM -> "Normal priority."
    AttentionPriority.LOW -> "Can wait. Delivered quietly."
    AttentionPriority.SILENT -> "Quiet. No sound or vibration."
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
