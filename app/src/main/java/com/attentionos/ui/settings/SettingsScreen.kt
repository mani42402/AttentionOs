package com.attentionos.ui.settings

import android.app.TimePickerDialog
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.attentionos.data.repository.StorageSummary
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.ActionRow
import com.attentionos.ui.components.OnDeviceBadge
import com.attentionos.ui.components.RowDivider
import com.attentionos.ui.components.SignalEyebrow
import com.attentionos.ui.components.FeatureSurfaceMutedColor
import com.attentionos.ui.components.SignalFeatureSurface
import com.attentionos.ui.components.SignalScreenHeader
import com.attentionos.ui.components.SettingsGroup
import com.attentionos.ui.components.ThemeModeSelector
import com.attentionos.ui.components.ToggleRow
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.ValueRow
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            SignalScreenHeader(
                title = stringResource(R.string.settings_settings),
                subtitle = stringResource(R.string.settings_every_behavior_remains_visible_and_under_your),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = Spacing.lg),
                trailing = { OnDeviceBadge() },
            )
        }

        item { SettingsStatusPanel(state, hasAccess) }

        item {
            SettingsGroup(stringResource(R.string.settings_protection)) {
                ToggleRow(
                    title = stringResource(R.string.settings_attention_mode),
                    subtitle = if (settings.focusMode) {
                        stringResource(R.string.settings_your_helper_manages_sound_and_vibration)
                    } else {
                        stringResource(R.string.settings_apps_control_their_own_notification_effects)
                    },
                    checked = settings.focusMode,
                    onCheckedChange = onFocusChanged,
                )
                RowDivider()
                ActionRow(
                    title = stringResource(R.string.settings_notification_access),
                    subtitle = if (hasAccess) stringResource(R.string.settings_connected) else stringResource(R.string.settings_not_connected_tap_to_allow),
                    onClick = onOpenNotificationAccess,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_what_may_interrupt_you)) {
                ToggleRow(
                    title = stringResource(R.string.settings_urgent_alerts),
                    subtitle = stringResource(R.string.settings_sound_for_security_calls_and_alarms),
                    checked = settings.criticalSound,
                    onCheckedChange = onCriticalSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_urgent_vibration),
                    subtitle = stringResource(R.string.settings_vibrate_for_urgent_alerts),
                    checked = settings.criticalVibration,
                    onCheckedChange = onCriticalVibrationChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_important_alerts),
                    subtitle = stringResource(R.string.settings_sound_for_things_that_likely_need_you),
                    checked = settings.highSound,
                    onCheckedChange = onHighSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_important_vibration),
                    subtitle = stringResource(R.string.settings_vibrate_for_important_alerts),
                    checked = settings.highVibration,
                    onCheckedChange = onHighVibrationChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_everyday_alerts),
                    subtitle = stringResource(R.string.settings_sound_for_normal_priority_notifications),
                    checked = settings.mediumSound,
                    onCheckedChange = onMediumSoundChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_everyday_vibration),
                    subtitle = stringResource(R.string.settings_vibrate_for_normal_priority_notifications),
                    checked = settings.mediumVibration,
                    onCheckedChange = onMediumVibrationChanged,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_appearance)) {
                ThemeModeSelector(
                    selected = ThemeMode.fromStorage(settings.themeMode),
                    onSelected = onThemeModeChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_use_wallpaper_colours),
                    subtitle = stringResource(R.string.settings_match_the_system_palette_instead_of_the),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_motion_effects),
                    subtitle = if (settings.motionEnabled) {
                        stringResource(R.string.settings_animated_transitions_and_charts)
                    } else {
                        stringResource(R.string.settings_reduced_motion_throughout)
                    },
                    checked = settings.motionEnabled,
                    onCheckedChange = onMotionChanged,
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_learning)) {
                ToggleRow(
                    title = stringResource(R.string.settings_learn_from_my_choices),
                    subtitle = stringResource(R.string.settings_adapt_to_which_notifications_you_act_on),
                    checked = settings.learningEnabled,
                    onCheckedChange = onLearningChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_daily_review_reminder),
                    subtitle = if (settings.reviewReminderEnabled) {
                        stringResource(R.string.settings_a_quiet_nudge_to_teach_your_helper)
                    } else {
                        stringResource(R.string.settings_review_whenever_you_choose)
                    },
                    checked = settings.reviewReminderEnabled,
                    onCheckedChange = {
                        if (it) onRequestNotificationPermission()
                        onReminderChanged(it)
                    },
                )
                if (settings.reviewReminderEnabled) {
                    RowDivider()
                    ReminderTimes(
                        selected = settings.reviewReminderTimes,
                        onChanged = onReminderTimesChanged,
                        onRequestPermission = onRequestNotificationPermission,
                    )
                }
                RowDivider()
                ActionRow(
                    title = stringResource(R.string.settings_reset_personalization),
                    subtitle = stringResource(R.string.settings_forget_learned_preferences_and_start_over),
                    onClick = { showResetDialog = true },
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_privacy)) {
                ToggleRow(
                    title = stringResource(R.string.settings_store_message_content),
                    subtitle = if (settings.storeContent) {
                        stringResource(R.string.settings_notification_text_is_saved_on_this_device)
                    } else {
                        stringResource(R.string.settings_off_only_categories_and_private_hashes_are)
                    },
                    checked = settings.storeContent,
                    onCheckedChange = onStoreContentChanged,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_hide_from_screenshots),
                    subtitle = if (settings.screenSecurity) {
                        stringResource(R.string.settings_content_is_hidden_from_screenshots_and_the)
                    } else {
                        stringResource(R.string.settings_screenshots_and_screen_recording_can_capture_content)
                    },
                    checked = settings.screenSecurity,
                    onCheckedChange = onScreenSecurityChanged,
                )
                RowDivider()
                RetentionChips(settings.retentionDays, onRetentionChanged)
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_what_s_stored_on_this_device)) {
                ValueRow(stringResource(R.string.settings_notifications_recorded), storage.notificationCount.toString())
                ValueRow(stringResource(R.string.settings_senders_remembered), storage.senderCount.toString())
                ValueRow(stringResource(R.string.settings_learning_examples), storage.trainingExampleCount.toString())
                ValueRow(
                    stringResource(R.string.settings_with_message_text),
                    if (storage.storedContentCount == 0) stringResource(R.string.settings_none) else storage.storedContentCount.toString(),
                )
                ValueRow(stringResource(R.string.settings_encrypted_database), formatBytes(storage.databaseBytes))
                RowDivider()
                Text(
                    stringResource(R.string.settings_all_of_this_stays_on_your_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }

        item {
            SettingsGroup(stringResource(R.string.settings_your_data)) {
                ActionRow(
                    title = stringResource(R.string.settings_export_learning_data),
                    subtitle = stringResource(R.string.settings_a_file_of_private_hashed_training_examples),
                    onClick = onExport,
                )
                RowDivider()
                ActionRow(
                    title = stringResource(R.string.settings_replay_guided_setup),
                    subtitle = stringResource(R.string.settings_revisit_the_introduction_without_losing_anything),
                    onClick = onReplayOnboarding,
                )
                RowDivider()
                ActionRow(
                    title = stringResource(R.string.settings_delete_all_local_data),
                    subtitle = stringResource(R.string.settings_history_learned_preferences_and_encryption_keys),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SignalEyebrow(stringResource(R.string.settings_attentionos_on_device_by_design))
                VSpace(Spacing.xs)
                Text(
                    stringResource(R.string.settings_your_controls_and_data_stay_with_you),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_everything),
            body = stringResource(R.string.settings_this_removes_all_decisions_learned_preferences_and),
            confirmLabel = stringResource(R.string.settings_delete),
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
            title = stringResource(R.string.settings_reset_personalization_2),
            body = stringResource(R.string.settings_your_helper_forgets_what_it_learned_about),
            confirmLabel = stringResource(R.string.settings_reset),
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
private fun SettingsStatusPanel(state: MainUiState, hasAccess: Boolean) {
    SignalFeatureSurface {
        Column {
            SignalEyebrow(stringResource(R.string.settings_control_center), color = SignalColors.Mint)
            VSpace(Spacing.sm)
            Text(
                text = when {
                    !hasAccess -> stringResource(R.string.settings_needs_notification_access)
                    state.settings.focusMode -> stringResource(R.string.settings_attention_mode_is_on)
                    else -> stringResource(R.string.settings_attention_mode_is_standing_by)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            VSpace(Spacing.xs)
            Text(
                stringResource(R.string.settings_all_analysis_and_preference_learning_run_locally),
                style = MaterialTheme.typography.bodyMedium,
                color = FeatureSurfaceMutedColor,
            )
            VSpace(Spacing.lg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StatusValue(
                    label = stringResource(R.string.settings_access),
                    value = if (hasAccess) stringResource(R.string.settings_connected) else stringResource(R.string.settings_off),
                    accent = if (hasAccess) SignalColors.Mint else SignalColors.Tangerine,
                    modifier = Modifier.weight(1f),
                )
                StatusValue(
                    label = stringResource(R.string.settings_learning_2),
                    value = if (state.settings.learningEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_paused),
                    accent = SignalColors.Sun,
                    modifier = Modifier.weight(1f),
                )
                StatusValue(
                    label = stringResource(R.string.settings_motion),
                    value = if (state.settings.motionEnabled) stringResource(R.string.settings_full) else stringResource(R.string.settings_reduced),
                    accent = SignalColors.Cream,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FeatureSurfaceMutedColor)
        VSpace(Spacing.xs)
        Text(value, style = MaterialTheme.typography.titleSmall, color = accent)
    }
}

@Composable
private fun ReminderTimes(
    selected: Set<Int>,
    onChanged: (Set<Int>) -> Unit,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val sorted = selected.sorted()
    Column(Modifier.padding(vertical = Spacing.md)) {
        Text(stringResource(R.string.settings_review_times), style = MaterialTheme.typography.bodyLarge)
        VSpace(Spacing.xs)
        Text(
            stringResource(R.string.settings_up_to_six_quiet_reminders_each_day),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            sorted.take(3).forEach { minute ->
                ReminderChip(
                    minute = minute,
                    canRemove = selected.size > 1,
                    onRemove = { onChanged(selected - minute) },
                )
            }
        }
        if (sorted.size > 3) {
            VSpace(Spacing.sm)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                sorted.drop(3).take(3).forEach { minute ->
                    ReminderChip(
                        minute = minute,
                        canRemove = selected.size > 1,
                        onRemove = { onChanged(selected - minute) },
                    )
                }
            }
        }
        VSpace(Spacing.sm)
        TextButton(
            onClick = {
                val fallback = sorted.lastOrNull() ?: 19 * 60
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onRequestPermission()
                        onChanged(selected + (hour * 60 + minute))
                    },
                    fallback / 60,
                    fallback % 60,
                    false,
                ).show()
            },
            enabled = selected.size < MAX_REMINDER_TIMES,
        ) {
            Text(if (selected.size < MAX_REMINDER_TIMES) stringResource(R.string.settings_add_another_time) else stringResource(R.string.settings_six_time_limit_reached))
        }
    }
}

/**
 * A scheduled reminder time, which the chip removes when tapped.
 *
 * A permanently-selected chip that deletes on tap is not self-explanatory, so the action is
 * spelled out for screen readers rather than left to the visual metaphor.
 */
@Composable
private fun ReminderChip(minute: Int, canRemove: Boolean, onRemove: () -> Unit) {
    val time = formatMinuteOfDay(minute)
    val spoken = stringResource(R.string.settings_remove_reminder_at, time)
    FilterChip(
        selected = true,
        enabled = canRemove,
        onClick = onRemove,
        label = { Text(time) },
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun RetentionChips(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = Spacing.md)) {
        Text(stringResource(R.string.settings_keep_history_for), style = MaterialTheme.typography.bodyLarge)
        VSpace(Spacing.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = selected == days,
                    onClick = { onSelect(days) },
                    label = { Text(pluralStringResource(R.plurals.settings_retention_days, days, days)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Human-readable byte count; this dashboard is meant to be read, not parsed. */
@Composable
@ReadOnlyComposable
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> stringResource(R.string.settings_0_kb)
    bytes < 1024 -> stringResource(R.string.settings_b, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.settings_kb, bytes / 1024)
    else -> stringResource(R.string.settings_mb, bytes / (1024.0 * 1024.0))
}

private fun formatMinuteOfDay(value: Int): String {
    val normalized = value.coerceIn(0, 23 * 60 + 59)
    val time = LocalTime.of(normalized / 60, normalized % 60)
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

private const val MAX_REMINDER_TIMES = 6

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
