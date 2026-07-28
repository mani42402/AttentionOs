package com.attentionos.service

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Karachi")

    @Test
    fun schedulesLaterTodayWhenSelectedTimeIsAhead() {
        val now = ZonedDateTime.of(2026, 7, 28, 13, 30, 0, 0, zone)

        assertEquals(
            5L * 60L * 60L * 1_000L + 30L * 60L * 1_000L,
            ReviewReminderScheduler.initialDelayMillis(now, 19 * 60),
        )
    }

    @Test
    fun schedulesTomorrowWhenSelectedTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 7, 28, 21, 15, 0, 0, zone)

        assertEquals(
            21L * 60L * 60L * 1_000L + 45L * 60L * 1_000L,
            ReviewReminderScheduler.initialDelayMillis(now, 19 * 60),
        )
    }

    @Test
    fun schedulesTomorrowWhenSelectedTimeEqualsNow() {
        val now = ZonedDateTime.of(2026, 7, 28, 9, 0, 0, 0, zone)

        assertEquals(
            24L * 60L * 60L * 1_000L,
            ReviewReminderScheduler.initialDelayMillis(now, 9 * 60),
        )
    }

    @Test
    fun supportsAnyMinuteWithinTheDay() {
        val now = ZonedDateTime.of(2026, 7, 28, 18, 20, 0, 0, zone)

        assertEquals(
            75L * 60L * 1_000L,
            ReviewReminderScheduler.initialDelayMillis(now, 19 * 60 + 35),
        )
    }
}
