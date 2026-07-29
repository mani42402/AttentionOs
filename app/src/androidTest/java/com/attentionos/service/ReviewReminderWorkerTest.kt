package com.attentionos.service

import android.Manifest
import android.app.NotificationManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.attentionos.core.di.attentionContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The reminder is the app's only self-initiated interruption, so it gets tested rather than
 * assumed.
 *
 * Scheduling is WorkManager's job; what matters here is that the worker honours the preference
 * and, when it does post, posts something that leads back to the review queue. A reminder that
 * fires after the user turned it off would be the single most annoying bug this app could ship.
 */
class ReviewReminderWorkerTest {

    /**
     * The worker deliberately posts nothing without this permission, so the test has to hold it
     * or it would be asserting the wrong branch.
     */
    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var context: Context
    private lateinit var notifications: NotificationManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancelAll()
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        runBlocking { settings().setReviewReminderEnabled(false) }
    }

    private fun settings() = context.attentionContainer.settingsRepository

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<ReviewReminderWorker>(context).build().doWork()
    }

    /**
     * `notify` crosses a binder, so the notification is not guaranteed to be visible the
     * instant `doWork` returns. Polls briefly rather than sleeping a fixed guess.
     */
    private fun posted(timeoutMillis: Long = 3_000L): StatusBarNotification? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            notifications.activeNotifications
                .firstOrNull { it.id == REMINDER_NOTIFICATION_ID }
                ?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }

    /** No wait: asserting an absence must not be able to pass by being early. */
    private fun postedNow(): StatusBarNotification? = notifications.activeNotifications
        .firstOrNull { it.id == REMINDER_NOTIFICATION_ID }

    @Test
    fun postsAReminderWhenEnabled() = runBlocking {
        settings().setReviewReminderEnabled(true)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val notification = posted()
        assertNotNull("an enabled reminder should post a notification", notification)
        // Silent and low priority: a reminder to do optional housekeeping must not behave like
        // the alerts the app exists to protect.
        assertNotNull(
            "the reminder must lead back into the app",
            notification!!.notification.contentIntent,
        )
    }

    @Test
    fun postsNothingWhenTheUserTurnedItOff() = runBlocking {
        settings().setReviewReminderEnabled(false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        SystemClock.sleep(500)
        assertNull("a disabled reminder must never post", postedNow())
    }

    private companion object {
        /** Mirrors the worker's private id; a reminder is a singleton notification. */
        const val REMINDER_NOTIFICATION_ID = 7401
    }
}
