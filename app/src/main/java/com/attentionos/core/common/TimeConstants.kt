package com.attentionos.core.common

/**
 * Time and scheduling constants shared across the app.
 *
 * These previously existed as private copies in several files, which meant a policy change
 * had to be made in more than one place to take effect.
 */
object TimeConstants {
    const val DAY_MILLIS = 86_400_000L

    /**
     * How long the shadow pilot runs before personalization may influence any decision.
     * The personal model is trained and evaluated during this window but cannot change
     * interruption behaviour.
     */
    const val PILOT_DURATION_MILLIS = 7L * DAY_MILLIS

    const val MINUTES_PER_DAY = 24 * 60

    /** Upper bound on user-scheduled review reminders, one WorkManager task each. */
    const val MAX_DAILY_REMINDERS = 6
}
