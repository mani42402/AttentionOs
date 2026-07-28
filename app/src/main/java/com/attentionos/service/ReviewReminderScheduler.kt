package com.attentionos.service

import com.attentionos.core.common.TimeConstants
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReviewReminderScheduler {

    /**
     * Fingerprint of the schedule this process last applied.
     *
     * The settings flow re-emits on every process start, and [sync] cancels and rebuilds every
     * reminder task. Without this guard each launch rewrote the WorkManager database and reset
     * the reminder schedule even when nothing had changed.
     */
    @Volatile
    private var appliedFingerprint: String? = null

    fun sync(context: Context, enabled: Boolean, times: Set<Int>) {
        val normalizedTimes = times
            .filter { it in 0 until TimeConstants.MINUTES_PER_DAY }
            .distinct()
            .sorted()
            .take(TimeConstants.MAX_DAILY_REMINDERS)
        val fingerprint = "$enabled:${normalizedTimes.joinToString(",")}"
        if (fingerprint == appliedFingerprint) return

        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        if (!enabled) {
            appliedFingerprint = fingerprint
            return
        }
        val now = ZonedDateTime.now()
        normalizedTimes
            .forEach { minuteOfDay ->
                val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(
                    24,
                    TimeUnit.HOURS,
                )
                    .setInitialDelay(
                        initialDelayMillis(now, minuteOfDay),
                        TimeUnit.MILLISECONDS,
                    )
                    .setConstraints(
                        Constraints.Builder().setRequiresBatteryNotLow(true).build(),
                    )
                    .addTag(WORK_TAG)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    "$WORK_NAME_PREFIX-$minuteOfDay",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        appliedFingerprint = fingerprint
    }

    /** Test-only: clears the memoised schedule so each case starts from a clean slate. */
    internal fun resetForTesting() {
        appliedFingerprint = null
    }

    internal fun initialDelayMillis(now: ZonedDateTime, minuteOfDay: Int): Long {
        val normalized = minuteOfDay.coerceIn(0, TimeConstants.MINUTES_PER_DAY - 1)
        var next = now
            .withHour(normalized / 60)
            .withMinute(normalized % 60)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }

    private const val LEGACY_WORK_NAME = "attention-daily-review"
    private const val WORK_NAME_PREFIX = "attention-review"
    private const val WORK_TAG = "attention-review-reminder"
}
