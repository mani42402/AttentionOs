package com.attentionos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityEngineTest {
    private val engine = PriorityEngine()

    @Test
    fun `security codes are never queued during focus`() {
        val result = engine.decide(
            signal = signal("Your verification code is 481229"),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
            memory = null,
        )

        assertEquals(NotificationCategory.SECURITY, result.category)
        assertFalse(result.shouldQueue)
        assertTrue(result.priority == AttentionPriority.CRITICAL)
    }

    @Test
    fun `promotion is queued during focus`() {
        val result = engine.decide(
            signal = signal("Summer sale — save up to 40% today"),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
            memory = null,
        )

        assertEquals(NotificationCategory.PROMOTION, result.category)
        assertTrue(result.shouldQueue)
        assertTrue(result.priority in setOf(AttentionPriority.LOW, AttentionPriority.SILENT))
    }

    @Test
    fun `urgent production message is high priority`() {
        val result = engine.decide(
            signal = signal("Urgent: production down, action required now"),
            context = AttentionContext(focusModeEnabled = false, hourOfDay = 14),
            memory = UserMemory(
                senderHash = "manager",
                importanceScore = 0.8f,
                openRate = 0.9f,
                interactionCount = 12,
            ),
        )

        assertEquals(NotificationCategory.WORK, result.category)
        assertTrue(result.priority in setOf(AttentionPriority.HIGH, AttentionPriority.CRITICAL))
        assertFalse(result.shouldQueue)
    }

    @Test
    fun `strongly urgent work is never queued during focus at night`() {
        val result = engine.decide(
            signal = signal("Production server is down. Action required immediately."),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 3),
            memory = null,
        )

        assertEquals(NotificationCategory.WORK, result.category)
        assertTrue(result.priority in setOf(AttentionPriority.HIGH, AttentionPriority.CRITICAL))
        assertFalse(result.shouldQueue)
    }

    @Test
    fun `financial alerts have a hard high priority floor`() {
        val result = engine.decide(
            signal = signal("A payment was debited from your bank account"),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 3),
            memory = null,
        )

        assertEquals(NotificationCategory.FINANCE, result.category)
        assertTrue(result.priority in setOf(AttentionPriority.HIGH, AttentionPriority.CRITICAL))
        assertFalse(result.shouldQueue)
    }

    @Test
    fun `calls and alarms have hard priority floors`() {
        val call = engine.decide(
            signal = signal("Incoming", categoryHint = "call"),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 3),
            memory = null,
        )
        val alarm = engine.decide(
            signal = signal("Wake up", categoryHint = "alarm"),
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 3),
            memory = null,
        )

        assertEquals(AttentionPriority.CRITICAL, call.priority)
        assertEquals(AttentionPriority.HIGH, alarm.priority)
        assertFalse(call.shouldQueue)
        assertFalse(alarm.shouldQueue)
    }

    @Test
    fun `trusted sender history raises an ambiguous message`() {
        val base = engine.decide(
            signal = signal("Can you take a look?"),
            context = AttentionContext(focusModeEnabled = false, hourOfDay = 15),
            memory = null,
        )
        val personalized = engine.decide(
            signal = signal("Can you take a look?"),
            context = AttentionContext(focusModeEnabled = false, hourOfDay = 15),
            memory = UserMemory(
                senderHash = "trusted",
                importanceScore = 0.95f,
                openRate = 0.95f,
                interactionCount = 20,
            ),
        )

        assertTrue(personalized.score > base.score)
    }

    private fun signal(
        text: String,
        categoryHint: String = "msg",
    ) = NotificationSignal(
        packageName = "com.example.messaging",
        title = "Sender",
        text = text,
        postedAt = 1_700_000_000_000,
        isConversation = true,
        isOngoing = false,
        categoryHint = categoryHint,
    )
}
