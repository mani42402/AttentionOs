package com.attentionos.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.core.di.attentionContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two destructive actions promise different things, and the difference is the whole point.
 *
 * "Reset personalization" says *"Your helper forgets what it learned about your preferences. Your
 * notification history stays."* — so it must clear the model and leave the events. "Delete all
 * local data" says everything goes. Wiring these two to the same call, or to each other's call,
 * would be indistinguishable from correct behaviour until a user lost history they were told they
 * would keep.
 */
class ResetSemanticsTest {

    private val container =
        InstrumentationRegistry.getInstrumentation().targetContext.attentionContainer

    @Test
    fun resettingPersonalizationClearsTheModelAndKeepsHistory() = runBlocking {
        val repository = container.attentionRepository
        val before = repository.recentEvents().first().size

        repository.resetPersonalizedModel()

        val progress = repository.personalizedModelProgress().first()
        assertEquals("the learned example count must be cleared", 0, progress.exampleCount)
        assertEquals("evaluation counters must be cleared", 0, progress.evaluationCount)
        assertEquals(
            "notification history must survive a personalization reset",
            before,
            repository.recentEvents().first().size,
        )
    }

    @Test
    fun deletingAllDataClearsHistoryToo() = runBlocking {
        val repository = container.attentionRepository

        repository.deleteAllUserData()

        assertTrue(
            "history must be gone after a full delete",
            repository.recentEvents().first().isEmpty(),
        )
        assertEquals(
            "the model must be gone after a full delete",
            0,
            repository.personalizedModelProgress().first().exampleCount,
        )
    }
}
