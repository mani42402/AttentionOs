package com.attentionos.ui.settings

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.attentionos.R
import com.attentionos.core.common.TimeConstants
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.ActionSettingRow
import com.attentionos.ui.components.ScreenHeader
import com.attentionos.ui.components.SettingsGroup
import com.attentionos.ui.components.SoftDivider
import com.attentionos.ui.components.ToggleSettingRow
import com.attentionos.ui.insights.ControlStatusCard
import com.attentionos.ui.theme.Coral500
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Sun500

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
    onDarkThemeChanged: (Boolean) -> Unit,
    onMotionChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onExport: () -> Unit,
    onResetPersonalizedModel: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetModelDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.settings_control_center),
                title = stringResource(R.string.settings_settings),
                description = stringResource(R.string.settings_every_behavior_clearly_under_your_control),
                horizontalPadding = 4.dp,
            )
        }
        item {
            ControlStatusCard(
                state = state,
                hasAccess = hasAccess,
            )
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_core_behavior)) {
                ToggleSettingRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.insights_attention_mode),
                    subtitle = if (state.settings.focusMode) {
                        "Your helper manages sound and vibration; nothing is hidden"
                    } else {
                        "Off — source apps control their normal effects"
                    },
                    checked = state.settings.focusMode,
                    onCheckedChange = onFocusChanged,
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_access)) {
                ActionSettingRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.insights_notification_access),
                    subtitle = if (hasAccess) "Active" else "Required for classification",
                    valueColor = if (hasAccess) Mint500 else Sun500,
                    onClick = onOpenNotificationAccess,
                )
                SoftDivider()
                ActionSettingRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_replay_guided_setup),
                    subtitle = stringResource(R.string.settings_review_guarantees_controls_and_the_safety_test),
                    onClick = onReplayOnboarding,
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_interruption_behavior)) {
                InterruptionPriorityRow(
                    priority = "Critical",
                    description = stringResource(R.string.settings_security_safety_and_immediate_action),
                    sound = state.settings.criticalSound,
                    vibration = state.settings.criticalVibration,
                    onSoundChanged = onCriticalSoundChanged,
                    onVibrationChanged = onCriticalVibrationChanged,
                )
                HorizontalDivider()
                InterruptionPriorityRow(
                    priority = "High",
                    description = stringResource(R.string.settings_important_and_time_sensitive),
                    sound = state.settings.highSound,
                    vibration = state.settings.highVibration,
                    onSoundChanged = onHighSoundChanged,
                    onVibrationChanged = onHighVibrationChanged,
                )
                HorizontalDivider()
                InterruptionPriorityRow(
                    priority = "Medium",
                    description = stringResource(R.string.settings_useful_but_usually_able_to_wait),
                    sound = state.settings.mediumSound,
                    vibration = state.settings.mediumVibration,
                    onSoundChanged = onMediumSoundChanged,
                    onVibrationChanged = onMediumVibrationChanged,
                )
                Text(
                    "Low and silent priorities never make AttentionOS sound or vibrate. The original notifications always remain visible.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_privacy_learning)) {
                ToggleSettingRow(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.settings_local_learning),
                    subtitle = stringResource(R.string.settings_remember_what_you_mark_important_or_can),
                    checked = state.settings.learningEnabled,
                    onCheckedChange = onLearningChanged,
                )
                HorizontalDivider()
                ToggleSettingRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_store_message_content),
                    subtitle = if (state.settings.storeContent) {
                        "Raw title and text stay in the local database"
                    } else {
                        "Off — only private features and hashes are kept"
                    },
                    checked = state.settings.storeContent,
                    onCheckedChange = onStoreContentChanged,
                )
                HorizontalDivider()
                ActionSettingRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.settings_reset_personalization),
                    subtitle = if (state.personalizedModel.exampleCount == 0) {
                        "No preferences learned yet"
                    } else {
                        "Clear ${state.personalizedModel.exampleCount} learned corrections"
                    },
                    valueColor = Coral500,
                    onClick = { showResetModelDialog = true },
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_appearance_effects)) {
                ToggleSettingRow(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.settings_dark_appearance),
                    subtitle = if (state.settings.darkTheme) {
                        "Midnight theme is active"
                    } else {
                        "Light theme is active"
                    },
                    checked = state.settings.darkTheme,
                    onCheckedChange = onDarkThemeChanged,
                )
                SoftDivider()
                ToggleSettingRow(
                    icon = Icons.Default.CheckCircle,
                    title = stringResource(R.string.settings_motion_effects),
                    subtitle = if (state.settings.motionEnabled) {
                        "Smooth transitions, graphs, and status effects"
                    } else {
                        "Reduced motion across the interface"
                    },
                    checked = state.settings.motionEnabled,
                    onCheckedChange = onMotionChanged,
                )
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_review_reminders)) {
                ToggleSettingRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_daily_review_reminders),
                    subtitle = if (state.settings.reviewReminderEnabled) {
                        val count = state.settings.reviewReminderTimes.size
                        "$count quiet ${if (count == 1) "reminder" else "reminders"} each day"
                    } else {
                        "Off — review whenever you choose"
                    },
                    checked = state.settings.reviewReminderEnabled,
                    onCheckedChange = {
                        onReminderChanged(it)
                        if (it) onRequestNotificationPermission()
                    },
                )
                if (state.settings.reviewReminderEnabled) {
                    SoftDivider()
                    ReminderScheduleEditor(
                        times = state.settings.reviewReminderTimes,
                        onTimesChanged = onReminderTimesChanged,
                    )
                }
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_data_retention)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Automatically delete decision history",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            FilterChip(
                                selected = state.settings.retentionDays == days,
                                onClick = { onRetentionChanged(days) },
                                label = { Text("$days days") },
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.settings_your_data)) {
                ActionSettingRow(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.settings_export_learning_data),
                    subtitle = stringResource(R.string.settings_json_lines_with_hashed_sender_identities),
                    onClick = onExport,
                )
                HorizontalDivider()
                ActionSettingRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.settings_delete_all_local_data),
                    subtitle = stringResource(R.string.settings_decisions_memory_and_training_examples),
                    valueColor = Coral500,
                    onClick = { showDeleteDialog = true },
                )
            }
        }
        item {
            Text(
                "AttentionOS 0.1 · On-device by design",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_local_data_2)) },
            text = {
                Text(
                    "This permanently removes your decision history, learned sender memory, and training examples. Settings stay unchanged.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (showResetModelDialog) {
        AlertDialog(
            onDismissRequest = { showResetModelDialog = false },
            title = { Text(stringResource(R.string.settings_reset_personalization_2)) },
            text = {
                Text(
                    "This clears only your personalized preferences and progress. " +
                        "Notification history and exported training examples remain.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetModelDialog = false
                        onResetPersonalizedModel()
                    },
                ) {
                    Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetModelDialog = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderScheduleEditor(
    times: Set<Int>,
    onTimesChanged: (Set<Int>) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberTimePickerState(
        initialHour = 19,
        initialMinute = 0,
        is24Hour = false,
    )
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_reminder_schedule), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose any time · up to 6 quiet reminders daily",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    "${times.size}/6",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        times.sorted().forEach { minuteOfDay ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Mint500, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatReminderTime(minuteOfDay),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { onTimesChanged(times - minuteOfDay) },
                        enabled = times.size > 1,
                    ) {
                        Text(stringResource(R.string.settings_remove))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = times.size < TimeConstants.MAX_DAILY_REMINDERS,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (times.size < TimeConstants.MAX_DAILY_REMINDERS) "Add another time" else "Six reminders maximum")
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.settings_add_reminder_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minuteOfDay = pickerState.hour * 60 + pickerState.minute
                        onTimesChanged(times + minuteOfDay)
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

internal fun formatReminderTime(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    val hour = normalized / 60
    val minute = normalized % 60
    val display = when (val twelveHour = hour % 12) {
        0 -> 12
        else -> twelveHour
    }
    return "$display:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

@Composable
internal fun InterruptionPriorityRow(
    priority: String,
    description: String,
    sound: Boolean,
    vibration: Boolean,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(priority, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = sound,
                onClick = { onSoundChanged(!sound) },
                label = { Text(if (sound) "Sound on" else "Sound off") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = vibration,
                onClick = { onVibrationChanged(!vibration) },
                label = { Text(if (vibration) "Vibrate on" else "Vibrate off") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
