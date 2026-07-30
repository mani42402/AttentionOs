package com.attentionos.service

import com.attentionos.data.settings.AppSettings
import com.attentionos.domain.AttentionPriority
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the device actually makes a noise is the platform's business. Whether an urgent alert
 * was *supposed* to is this app's central promise, and until now it was only checkable by ear.
 *
 * The failure this guards against is silent in the worst way: reading `highSound` where
 * `criticalSound` was meant would mute security alerts for anyone who turned the Important level
 * off, and nothing in the interface would show it.
 */
class InterruptionPlanTest {

    private val allOn = AppSettings(
        criticalSound = true,
        criticalVibration = true,
        highSound = true,
        highVibration = true,
        mediumSound = true,
        mediumVibration = true,
    )

    private val allOff = AppSettings(
        criticalSound = false,
        criticalVibration = false,
        highSound = false,
        highVibration = false,
        mediumSound = false,
        mediumVibration = false,
    )

    @Test
    fun `each level reads its own preference and no other`() {
        // One level enabled at a time. If any mapping is crossed, exactly one of these fails.
        val cases = listOf(
            AttentionPriority.CRITICAL to allOff.copy(criticalSound = true),
            AttentionPriority.HIGH to allOff.copy(highSound = true),
            AttentionPriority.MEDIUM to allOff.copy(mediumSound = true),
        )
        for ((priority, settings) in cases) {
            assertTrue(
                "$priority should sound when only its own preference is on",
                interruptionPlanFor(priority, settings).sound,
            )
            for (other in cases.map { it.first }.filter { it != priority }) {
                assertFalse(
                    "$other must not sound from $priority's preference",
                    interruptionPlanFor(other, settings).sound,
                )
            }
        }
    }

    @Test
    fun `vibration is mapped independently of sound`() {
        val soundOnly = allOff.copy(criticalSound = true)
        val plan = interruptionPlanFor(AttentionPriority.CRITICAL, soundOnly)
        assertTrue(plan.sound)
        assertFalse("enabling sound must not enable vibration", plan.vibration)

        val vibrationOnly = allOff.copy(criticalVibration = true)
        val other = interruptionPlanFor(AttentionPriority.CRITICAL, vibrationOnly)
        assertFalse("enabling vibration must not enable sound", other.sound)
        assertTrue(other.vibration)
    }

    @Test
    fun `low and silent never interrupt however the preferences are set`() {
        // This is a floor rather than a preference: "can wait" and "quiet" are the two levels
        // whose whole meaning is that they do not interrupt. No setting may override it.
        for (priority in listOf(AttentionPriority.LOW, AttentionPriority.SILENT)) {
            val plan = interruptionPlanFor(priority, allOn)
            assertTrue("$priority must stay silent even with every preference on", plan.silent)
        }
    }

    @Test
    fun `everything above low interrupts when the user has allowed it`() {
        for (priority in listOf(
            AttentionPriority.CRITICAL,
            AttentionPriority.HIGH,
            AttentionPriority.MEDIUM,
        )) {
            val plan = interruptionPlanFor(priority, allOn)
            assertTrue("$priority should sound when allowed", plan.sound)
            assertTrue("$priority should vibrate when allowed", plan.vibration)
        }
    }

    @Test
    fun `nothing interrupts when the user has allowed nothing`() {
        for (priority in AttentionPriority.entries) {
            assertTrue(
                "$priority must be silent with all preferences off",
                interruptionPlanFor(priority, allOff).silent,
            )
        }
    }

    @Test
    fun `the urgent pattern is distinct so it can be recognised without looking`() {
        val urgent = vibrationPatternFor(AttentionPriority.CRITICAL)
        assertArrayEquals(longArrayOf(0, 180, 90, 240), urgent)
        for (priority in AttentionPriority.entries.filter { it != AttentionPriority.CRITICAL }) {
            assertArrayEquals(
                "only CRITICAL gets the double pulse",
                longArrayOf(0, 160),
                vibrationPatternFor(priority),
            )
        }
    }

    @Test
    fun `the default settings let protected levels through`() {
        // A user who never opens Settings must still be reachable for the levels the app
        // promises to protect.
        val defaults = AppSettings()
        assertTrue(
            "urgent alerts must reach a user who changed nothing",
            interruptionPlanFor(AttentionPriority.CRITICAL, defaults).sound,
        )
        assertEquals(
            "and quiet must stay quiet by default",
            true,
            interruptionPlanFor(AttentionPriority.SILENT, defaults).silent,
        )
    }
}
