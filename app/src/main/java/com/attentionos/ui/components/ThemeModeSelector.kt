package com.attentionos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics

/**
 * Segmented control for System / Light / Dark.
 *
 * Replaces a "Dark appearance" switch that could not express "follow the system" at all — so the
 * app ignored a preference the OS had already collected, and a user in system dark mode saw a
 * bright app until they went looking for the setting.
 *
 * Carries proper radio-group semantics, which the previous chips did not: screen readers now
 * announce the options as a single choice with one selected, rather than three unrelated
 * buttons.
 */
@Composable
internal fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, Radius.pill)
            .padding(Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeOption(
                mode = mode,
                selected = mode == selected,
                onSelect = {
                    haptics.select()
                    onSelected(mode)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = motionEnabled()
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = Motion.snappy(enabled),
        label = "theme-option-background",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.snappy(enabled),
        label = "theme-option-content",
    )

    Box(
        modifier = modifier
            .height(Spacing.huge)
            .background(background, Radius.pill)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mode.label(),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "System"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

@Preview(name = "Theme selector · light")
@Composable
private fun ThemeModeSelectorPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        ThemeModeSelector(selected = ThemeMode.System, onSelected = {})
    }
}

@Preview(name = "Theme selector · dark")
@Composable
private fun ThemeModeSelectorDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        ThemeModeSelector(selected = ThemeMode.Dark, onSelected = {})
    }
}
