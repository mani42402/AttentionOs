package com.attentionos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.rememberHaptics

/**
 * Grouped settings rows.
 *
 * The previous rows put the click target on the `Switch` alone, so the title and subtitle were
 * dead to touch and TalkBack read each row as three unrelated nodes. These use `toggleable` on
 * the whole row, which both widens the target to the full width and merges the row into a single
 * announcement with its state.
 */

@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        GroupLabel(title)
        AttentionCard(
            tone = MaterialTheme.colorScheme.surfaceContainerLow,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.xs,
            ),
            content = content,
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.select()
                    onCheckedChange(it)
                },
            )
            .defaultMinSize(minHeight = Spacing.giant)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        icon?.let { RowIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row owns the semantics; the switch is decorative from a11y's point of view.
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
internal fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Spacing.giant)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        icon?.let { RowIcon(it, tint = titleColor) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Read-only label/value pair, used by the privacy dashboard. */
@Composable
internal fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(alpha = 0.10f), Radius.card),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Preview(name = "Setting rows · light")
@Composable
private fun SettingRowsPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Column(Modifier.padding(Spacing.lg)) {
            SettingsGroup("Protection") {
                ToggleRow(
                    title = "Attention Mode",
                    subtitle = "Your helper manages sound and vibration",
                    checked = true,
                    onCheckedChange = {},
                )
                RowDivider()
                ValueRow("Notifications recorded", "128")
            }
        }
    }
}

@Preview(name = "Setting rows · dark")
@Composable
private fun SettingRowsDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Column(Modifier.padding(Spacing.lg)) {
            SettingsGroup("Protection") {
                ToggleRow(
                    title = "Attention Mode",
                    subtitle = "Your helper manages sound and vibration",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }
    }
}
