package com.attentionos.data

import com.attentionos.core.common.TimeConstants
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "attention_settings",
)

data class AppSettings(
    val focusMode: Boolean = false,
    val storeContent: Boolean = false,
    val learningEnabled: Boolean = true,
    val retentionDays: Int = 30,
    val criticalSound: Boolean = true,
    val criticalVibration: Boolean = true,
    val highSound: Boolean = true,
    val highVibration: Boolean = true,
    val mediumSound: Boolean = false,
    val mediumVibration: Boolean = false,
    val reviewReminderEnabled: Boolean = false,
    val reviewReminderTimes: Set<Int> = setOf(19 * 60),
    val darkTheme: Boolean = false,
    val motionEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val pilotStartedAt: Long = 0L,
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            focusMode = values[FOCUS_MODE] ?: false,
            storeContent = values[STORE_CONTENT] ?: false,
            learningEnabled = values[LEARNING_ENABLED] ?: true,
            retentionDays = values[RETENTION_DAYS] ?: 30,
            criticalSound = values[CRITICAL_SOUND] ?: true,
            criticalVibration = values[CRITICAL_VIBRATION] ?: true,
            highSound = values[HIGH_SOUND] ?: true,
            highVibration = values[HIGH_VIBRATION] ?: true,
            mediumSound = values[MEDIUM_SOUND] ?: false,
            mediumVibration = values[MEDIUM_VIBRATION] ?: false,
            reviewReminderEnabled = values[REVIEW_REMINDER_ENABLED] ?: false,
            reviewReminderTimes = values[REVIEW_REMINDER_TIMES]
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it in 0 until TimeConstants.MINUTES_PER_DAY }
                ?.toSet()
                ?.takeIf(Set<Int>::isNotEmpty)
                ?: setOf((values[REVIEW_REMINDER_HOUR] ?: 19) * 60),
            darkTheme = values[DARK_THEME] ?: false,
            motionEnabled = values[MOTION_ENABLED] ?: true,
            onboardingComplete = values[ONBOARDING_COMPLETE] ?: false,
            pilotStartedAt = values[PILOT_STARTED_AT] ?: 0L,
        )
    }

    suspend fun setFocusMode(enabled: Boolean) = update(FOCUS_MODE, enabled)
    suspend fun setStoreContent(enabled: Boolean) = update(STORE_CONTENT, enabled)
    suspend fun setLearningEnabled(enabled: Boolean) = update(LEARNING_ENABLED, enabled)
    suspend fun setCriticalSound(enabled: Boolean) = update(CRITICAL_SOUND, enabled)
    suspend fun setCriticalVibration(enabled: Boolean) = update(CRITICAL_VIBRATION, enabled)
    suspend fun setHighSound(enabled: Boolean) = update(HIGH_SOUND, enabled)
    suspend fun setHighVibration(enabled: Boolean) = update(HIGH_VIBRATION, enabled)
    suspend fun setMediumSound(enabled: Boolean) = update(MEDIUM_SOUND, enabled)
    suspend fun setMediumVibration(enabled: Boolean) = update(MEDIUM_VIBRATION, enabled)
    suspend fun setReviewReminderEnabled(enabled: Boolean) =
        update(REVIEW_REMINDER_ENABLED, enabled)
    suspend fun setDarkTheme(enabled: Boolean) = update(DARK_THEME, enabled)
    suspend fun setMotionEnabled(enabled: Boolean) = update(MOTION_ENABLED, enabled)

    suspend fun setReviewReminderTimes(times: Set<Int>) {
        val normalized = times
            .asSequence()
            .filter { it in 0 until TimeConstants.MINUTES_PER_DAY }
            .distinct()
            .sorted()
            .take(TimeConstants.MAX_DAILY_REMINDERS)
            .map(Int::toString)
            .toSet()
        context.settingsDataStore.edit {
            it[REVIEW_REMINDER_TIMES] = normalized.ifEmpty {
                setOf((19 * 60).toString())
            }
        }
    }

    suspend fun completeOnboarding(completedAt: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit {
            it[ONBOARDING_COMPLETE] = true
            if ((it[PILOT_STARTED_AT] ?: 0L) == 0L) it[PILOT_STARTED_AT] = completedAt
        }
    }

    suspend fun replayOnboarding() {
        context.settingsDataStore.edit { it[ONBOARDING_COMPLETE] = false }
    }

    suspend fun setRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[RETENTION_DAYS] = days.coerceIn(1, 90) }
    }

    private suspend fun update(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private companion object {
        val FOCUS_MODE = booleanPreferencesKey("focus_mode")
        val STORE_CONTENT = booleanPreferencesKey("store_content")
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val CRITICAL_SOUND = booleanPreferencesKey("critical_sound")
        val CRITICAL_VIBRATION = booleanPreferencesKey("critical_vibration")
        val HIGH_SOUND = booleanPreferencesKey("high_sound")
        val HIGH_VIBRATION = booleanPreferencesKey("high_vibration")
        val MEDIUM_SOUND = booleanPreferencesKey("medium_sound")
        val MEDIUM_VIBRATION = booleanPreferencesKey("medium_vibration")
        val REVIEW_REMINDER_ENABLED = booleanPreferencesKey("review_reminder_enabled")
        val REVIEW_REMINDER_HOUR = intPreferencesKey("review_reminder_hour")
        val REVIEW_REMINDER_TIMES = stringSetPreferencesKey("review_reminder_times")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val MOTION_ENABLED = booleanPreferencesKey("motion_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val PILOT_STARTED_AT = longPreferencesKey("pilot_started_at")
    }
}
