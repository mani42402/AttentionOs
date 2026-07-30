package com.attentionos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AttentionPolicy] is the single source of truth for the score ladder and the
 * never-suppress rules. Both used to be duplicated across the scoring engine and the
 * personalization policy, where they could silently drift apart. These tests pin the
 * shared behaviour so a future edit cannot quietly weaken a safety guarantee.
 */
class AttentionPolicyTest {

    @Test
    fun `score ladder maps each band to its priority`() {
        assertEquals(AttentionPriority.CRITICAL, AttentionPolicy.priorityFor(1f))
        assertEquals(AttentionPriority.CRITICAL, AttentionPolicy.priorityFor(0.86f))
        assertEquals(AttentionPriority.HIGH, AttentionPolicy.priorityFor(0.85f))
        assertEquals(AttentionPriority.HIGH, AttentionPolicy.priorityFor(0.68f))
        assertEquals(AttentionPriority.MEDIUM, AttentionPolicy.priorityFor(0.67f))
        assertEquals(AttentionPriority.MEDIUM, AttentionPolicy.priorityFor(0.46f))
        assertEquals(AttentionPriority.LOW, AttentionPolicy.priorityFor(0.45f))
        assertEquals(AttentionPriority.LOW, AttentionPolicy.priorityFor(0.24f))
        assertEquals(AttentionPriority.SILENT, AttentionPolicy.priorityFor(0.23f))
        assertEquals(AttentionPriority.SILENT, AttentionPolicy.priorityFor(0f))
    }

    @Test
    fun `ladder is monotonic across the full score range`() {
        var previous = AttentionPriority.SILENT
        var score = 0f
        while (score <= 1f) {
            val current = AttentionPolicy.priorityFor(score)
            // Enum order runs CRITICAL..SILENT, so rising scores must never move later.
            assertTrue(
                "priority regressed at score=$score",
                current.ordinal <= previous.ordinal,
            )
            previous = current
            score += 0.01f
        }
    }

    @Test
    fun `security and finance are always protected`() {
        for (category in listOf(NotificationCategory.SECURITY, NotificationCategory.FINANCE)) {
            assertTrue(
                "$category must be protected",
                AttentionPolicy.isSafetyProtected(category, semanticUrgency = 0f, categoryHint = null),
            )
        }
    }

    @Test
    fun `calls and alarms are protected regardless of category`() {
        assertTrue(
            AttentionPolicy.isSafetyProtected(
                NotificationCategory.OTHER,
                semanticUrgency = 0f,
                categoryHint = AttentionPolicy.CATEGORY_HINT_CALL,
            ),
        )
        assertTrue(
            AttentionPolicy.isSafetyProtected(
                NotificationCategory.OTHER,
                semanticUrgency = 0f,
                categoryHint = AttentionPolicy.CATEGORY_HINT_ALARM,
            ),
        )
    }

    @Test
    fun `strong urgency protects an otherwise ordinary alert`() {
        assertTrue(
            AttentionPolicy.isSafetyProtected(
                NotificationCategory.WORK,
                semanticUrgency = AttentionPolicy.STRONG_URGENCY_THRESHOLD,
                categoryHint = null,
            ),
        )
        assertFalse(
            AttentionPolicy.isSafetyProtected(
                NotificationCategory.WORK,
                semanticUrgency = AttentionPolicy.STRONG_URGENCY_THRESHOLD - 0.01f,
                categoryHint = null,
            ),
        )
    }

    @Test
    fun `promotions are not protected`() {
        assertFalse(
            AttentionPolicy.isSafetyProtected(
                NotificationCategory.PROMOTION,
                semanticUrgency = 0.1f,
                categoryHint = null,
            ),
        )
    }

    @Test
    fun `queueing only applies to low and silent while focus mode is on`() {
        for (priority in AttentionPriority.entries) {
            val queued = AttentionPolicy.shouldQueue(focusModeEnabled = true, priority = priority)
            val expected = priority == AttentionPriority.LOW || priority == AttentionPriority.SILENT
            assertEquals("queueing mismatch for $priority", expected, queued)
        }
    }

    @Test
    fun `nothing is queued while focus mode is off`() {
        for (priority in AttentionPriority.entries) {
            assertFalse(
                "$priority must not be queued outside focus mode",
                AttentionPolicy.shouldQueue(focusModeEnabled = false, priority = priority),
            )
        }
    }
}
