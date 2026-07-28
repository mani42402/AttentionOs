package com.attentionos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.attentionos.data.repository.StorageSummary
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.ActionRow
import com.attentionos.ui.components.RowDivider
import com.attentionos.ui.components.SettingsGroup
import com.attentionos.ui.components.ThemeModeSelector
import com.attentionos.ui.components.ToggleRow
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.ValueRow
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode

/**
 * Settings.
 *
 * Regrouped around what a user is trying to do — control interruptions, choose how it looks,
 * understand what is stored — rather than mirroring the order features were built in. Every row
 * is a single merged touch target with correct semantics; the previous rows put the target on
 * the switch alone, leaving the label dead to touch and reading as three separate nodes to a
 * screen reader.
 */
@Composable
internal fun SettingsScreen(
    state: MainUiState,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onStoreContentChanged: (Boolean) -> Unit,
    onLearningChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onMediumSoundChanged: (Boolean) -> Unit,
    onMediumVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
    onReminderTimesChanged: (Set<Int>) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onMotionChanged: (Boolean) -> Unit,
    onScreenSecurityChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onExport: () -> Unit,
    onResetPersonalizedModel: () -> Unit,
    onDelete: () -> Unit,
    storage: StorageSummary,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val settings = state.settings

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                VSpace(Spacing.lg)
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                VSpace(Spacing.xs)
                Text(
                    "Every behaviour, under your control.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsGroup("Protection") {
                ToggleRow(
                    title = "Attention Mode",
                    subtitle = if (settings.focusMode) {
                        "Your helper manages sound and vibration"
                    } else {
                        "Apps control their own notification effects"
                    },
                    checked = settings.focusMode,
                    onCheckedChange = onFocusChanged,
                )
                RowDivider()
                ActionRow(
                    title = "Notification access",
                    subtitle = if (hasAccess) "Connected" else "Not connected — tap to allow",
                    onClick = onOpenNotificationAccess,
                )
            }
        }

        item {
            SettingsGroup("What may interrupt you") {
                ToggleRow(
                    title = "Urgent alerts",
                    subtitle = "Sound for security, calls and alarms",
                    checked = settings.criticalSound,
                    onCheckedChange = onCriticalSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Urgent vibration",
                    subtitle = "Vibrate for urgent alerts",
                    checked = settings.criticalVibration,
                    onCheckedChange = onCriticalVibrationChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Important alerts",
                    subtitle = "Sound for things that likely need you",
                    checked = settings.highSound,
                    onCheckedChange = onHighSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Important vibration",
                    subtitle = "Vibrate for important alerts",
                    checked = settings.highVibration,
                    onCheckedChange = onHighVibrationChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Everyday alerts",
                    subtitle = "Sound for normal-priority notifications",
                    checked = settings.mediumSound,
                    onCheckedChange = onMediumSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Everyday vibration",
                    subtitle = "Vibrate for normal-priority notifications",
                    checked = settings.mediumVibration,
                    onCheckedChange = onMediumVibrationChanged,
                )
            }
        }

        item {
            SettingsGroup("Appearance") {
                ThemeModeSelector(
                    selected = ThemeMode.fromStorage(settings.themeMode),
                    onSelected = onThemeModeChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Use wallpaper colours",
                    subtitle = "Match the system palette instead of the app's own",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Motion effects",
                    subtitle = if (settings.motionEnabled) {
                        "Animated transitions and charts"
                    } else {
                        "Reduced motion throughout"
                    },
                    checked = settings.motionEnabled,
                    onCheckedChange = onMotionChanged,
                )
            }
        }

        item {
            SettingsGroup("Learning") {
                ToggleRow(
                    title = "Learn from my choices",
                    subtitle = "Adapt to which notifications you act on",
                    checked = settings.learningEnabled,
                    onCheckedChange = onLearningChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Daily review reminder",
                    subtitle = if (settings.reviewReminderEnabled) {
                        "A quiet nudge to teach your helper"
                    } else {
                        "Review whenever you choose"
                    },
                    checked = settings.reviewReminderEnabled,
                    onCheckedChange = onReminderChanged,
                )
                RowDivider()
                ActionRow(
                    title = "Reset personalization",
                    subtitle = "Forget learned preferences and start over",
                    onClick = { showResetDialog = true },
                )
            }
        }

        item {
            SettingsGroup("Privacy") {
                ToggleRow(
                    title = "Store message content",
                    subtitle = if (settings.storeContent) {
                        "Notification text is saved on this device"
                    } else {
                        "Off — only categories and private hashes are kept"
                    },
                    checked = settings.storeContent,
                    onCheckedChange = onStoreContentChanged,
                )
                RowDivider()
                ToggleRow(
                    title = "Hide from screenshots",
                    subtitle = if (settings.screenSecurity) {
                        "Content is hidden from screenshots and the recents preview"
                    } else {
                        "Screenshots and screen recording can capture content"
                    },
                    checked = settings.screenSecurity,
                    onCheckedChange = onScreenSecurityChanged,
                )
                RowDivider()
                RetentionChips(settings.retentionDays, onRetentionChanged)
            }
        }

        item {
            SettingsGroup("What's stored on this device") {
                ValueRow("Notifications recorded", storage.notificationCount.toString())
                ValueRow("Senders remembered", storage.senderCount.toString())
                ValueRow("Learning examples", storage.trainingExampleCount.toString())
                ValueRow(
                    "With message text",
                    if (storage.storedContentCount == 0) "None" else storage.storedContentCount.toString(),
                )
                ValueRow("Encrypted database", formatBytes(storage.databaseBytes))
                RowDivider()
                Text(
                    "All of this stays on your device, encrypted. Deleting it destroys the " +
                        "encryption keys too, so nothing can be recovered afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }

        item {
            SettingsGroup("Your data") {
                ActionRow(
                    title = "Export learning data",
                    subtitle = "A file of private, hashed training examples",
                    onClick = onExport,
                )
                RowDivider()
                ActionRow(
                    title = "Replay guided setup",
                    subtitle = "Revisit the introduction without losing anything",
                    onClick = onReplayOnboarding,
                )
                RowDivider()
                ActionRow(
                    title = "Delete all local data",
                    subtitle = "History, learned preferences and encryption keys",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true },
                )
            }
        }

        item {
            Text(
                "AttentionOS · on-device by design",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
            )
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete everything?",
            body = "This removes all decisions, learned preferences and the encryption keys " +
                "protecting them. It cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Reset personalization?",
            body = "Your helper forgets what it learned about your preferences. Your " +
                "notification history stays.",
            confirmLabel = "Reset",
            destructive = false,
            onConfirm = {
                showResetDialog = false
                onResetPersonalizedModel()
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun RetentionChips(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = Spacing.md)) {
        Text("Keep history for", style = MaterialTheme.typography.bodyLarge)
        VSpace(Spacing.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = selected == days,
                    onClick = { onSelect(days) },
                    label = { Text("$days days") },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Human-readable byte count; this dashboard is meant to be read, not parsed. */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Preview(name = "Settings · light", heightDp = 1400)
@Composable
private fun SettingsPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                state = MainUiState(isLoading = false),
                hasAccess = true,
                onFocusChanged = {}, onOpenNotificationAccess = {}, onStoreContentChanged = {},
                onLearningChanged = {}, onCriticalSoundChanged = {}, onCriticalVibrationChanged = {},
                onHighSoundChanged = {}, onHighVibrationChanged = {}, onMediumSoundChanged = {},
                onMediumVibrationChanged = {}, onReminderChanged = {}, onReminderTimesChanged = {},
                onThemeModeChanged = {}, onDynamicColorChanged = {}, onMotionChanged = {},
                onScreenSecurityChanged = {}, onRequestNotificationPermission = {},
                onRetentionChanged = {}, onReplayOnboarding = {}, onExport = {},
                onResetPersonalizedModel = {}, onDelete = {},
                storage = StorageSummary(notificationCount = 128, senderCount = 14),
            )
        }
    }
}
