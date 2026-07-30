package com.attentionos.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.core.common.TimeConstants
import com.attentionos.data.db.AttentionDatabase
import com.attentionos.data.db.NotificationEventEntity
import com.attentionos.data.db.TrainingExampleEntity
import com.attentionos.data.db.UserMemoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Retention is the only thing standing between the app and unbounded growth, and it had never
 * been exercised against data old enough to delete.
 *
 * Insertions here carry backdated timestamps rather than waiting, so the horizon and the row caps
 * are both tested for real. An off-by-one in a `<` or a `LIMIT` would otherwise surface as a
 * database that quietly grows for a month, or — far worse — one that deletes what the user was
 * told would be kept.
 */
class RetentionTest {

    private lateinit var database: AttentionDatabase
    private lateinit var dao: com.attentionos.data.db.AttentionDao
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        // In-memory and unencrypted: this is about the retention queries, and using the real
        // database would delete the user's data on the test device.
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AttentionDatabase::class.java,
        ).build()
        dao = database.attentionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun event(id: Long, ageDays: Long) = NotificationEventEntity(
        notificationKey = "key-$id",
        packageName = "com.example.app",
        appLabel = "Example",
        senderHash = "hash-$id",
        title = null,
        message = null,
        postedAt = now - ageDays * TimeConstants.DAY_MILLIS,
        priority = "MEDIUM",
        category = "OTHER",
        score = 0.5f,
        explanation = "test",
        queued = false,
    )

    private fun training(id: Long, ageDays: Long) = TrainingExampleEntity(
        sourceEventId = id,
        featuresJson = """{"app":"com.example.app"}""",
        expectedPriority = "MEDIUM",
        createdAt = now - ageDays * TimeConstants.DAY_MILLIS,
    )

    private fun memory(id: Long, ageDays: Long) = UserMemoryEntity(
        senderHash = "sender-$id",
        importanceScore = 0.5f,
        openCount = 1,
        dismissCount = 0,
        interactionCount = 1,
        averageResponseSeconds = 10,
        updatedAt = now - ageDays * TimeConstants.DAY_MILLIS,
    )

    @Test
    fun eventsOlderThanTheRetentionHorizonAreDeletedAndNewerOnesSurvive() = runBlocking {
        // 30-day retention: 10 and 29 days old must stay, 31 and 400 must go.
        dao.insertEvent(event(1, ageDays = 10))
        dao.insertEvent(event(2, ageDays = 29))
        dao.insertEvent(event(3, ageDays = 31))
        dao.insertEvent(event(4, ageDays = 400))

        val deleted = dao.deleteEventsBefore(now - 30 * TimeConstants.DAY_MILLIS)

        assertEquals("only the two aged rows should be deleted", 2, deleted)
        val survivors = dao.observeRecent().first().map { it.notificationKey }.sorted()
        assertEquals(listOf("key-1", "key-2"), survivors)
    }

    @Test
    fun aRowExactlyOnTheHorizonIsKept() = runBlocking {
        // The boundary is `postedAt < before`, so a row sitting precisely on it survives. Worth
        // pinning: flipping this to `<=` would silently shorten every user's history by a day.
        val horizon = now - 30 * TimeConstants.DAY_MILLIS
        dao.insertEvent(event(1, ageDays = 0).copy(postedAt = horizon))

        assertEquals(0, dao.deleteEventsBefore(horizon))
        assertEquals(1, dao.observeRecent().first().size)
    }

    @Test
    fun trainingRowsAreCappedKeepingTheMostRecent() = runBlocking {
        // Ages descend, so id 1 is the newest.
        repeat(12) { index -> dao.insertTrainingExample(training(index.toLong(), ageDays = index.toLong())) }

        dao.trimTrainingTo(5)

        val kept = dao.trainingForExport()
        assertEquals("the cap must be honoured exactly", 5, kept.size)
        val newest = now - 5 * TimeConstants.DAY_MILLIS
        assertTrue(
            "the cap must keep the newest rows, not an arbitrary five",
            kept.all { it.createdAt > newest - 1 },
        )
    }

    @Test
    fun senderMemoryIsPrunedByItsOwnLongerHorizon() = runBlocking {
        // Sender memory intentionally outlives events: it is small, and re-learning a sender
        // costs the user interactions. 180 days, not the event retention setting.
        dao.upsertMemory(memory(1, ageDays = 90))
        dao.upsertMemory(memory(2, ageDays = 179))
        dao.upsertMemory(memory(3, ageDays = 181))

        val deleted = dao.deleteMemoryBefore(now - 180 * TimeConstants.DAY_MILLIS)

        assertEquals("only memory past 180 days should go", 1, deleted)
        assertEquals(2, dao.observeSenderCount().first())
    }

    @Test
    fun senderMemoryIsCappedKeepingTheMostRecentlyUpdated() = runBlocking {
        repeat(8) { index -> dao.upsertMemory(memory(index.toLong(), ageDays = index.toLong())) }

        dao.trimMemoryTo(3)

        assertEquals(3, dao.observeSenderCount().first())
    }
}
