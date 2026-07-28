package com.attentionos.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReviewReminderScheduler {
    fun sync(context: Context, enabled: Boolean, times: Set<Int>) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        if (!enabled) {
            return
        }
        val now = ZonedDateTime.now()
        times
            .asSequence()
            .filter { it in 0 until MINUTES_PER_DAY }
            .distinct()
            .sorted()
            .take(MAX_DAILY_REMINDERS)
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
    }

    internal fun initialDelayMillis(now: ZonedDateTime, minuteOfDay: Int): Long {
        val normalized = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
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
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MAX_DAILY_REMINDERS = 6
}
