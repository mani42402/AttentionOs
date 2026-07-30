package com.attentionos.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for [AttentionDatabase].
 *
 * The app ships a hand-written migration per schema version and, today, still falls back to
 * dropping every table when one is missing or wrong. That fallback destroys a user's entire
 * history, learned sender memory and personalized model silently. These tests exercise each
 * migration against real schema files so a defect surfaces here rather than as data loss on a
 * user's device.
 */
@RunWith(AndroidJUnit4::class)
class AttentionDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AttentionDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesThroughEveryVersion() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALL_MIGRATIONS).close()
    }

    @Test
    fun migrationOneToTwoPreservesExistingRows() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO notification_events
                    (notificationKey, packageName, appLabel, senderHash, title, message,
                     postedAt, priority, category, score, explanation, queued, action, actedAt)
                VALUES ('key-1', 'com.example', 'Example', 'hash-1', 'Title', 'Body',
                        1000, 'HIGH', 'WORK', 0.7, 'because', 0, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, *ALL_MIGRATIONS)
        migrated.query("SELECT notificationKey, priority FROM notification_events").use { cursor ->
            assertTrue("row should survive the migration", cursor.moveToFirst())
            assertEquals("key-1", cursor.getString(0))
            assertEquals("HIGH", cursor.getString(1))
            assertEquals("exactly one row expected", 1, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrationFiveToSixAddsTheReadPathIndexes() {
        helper.createDatabase(TEST_DB, 5).close()
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AttentionDatabase.MIGRATION_5_6,
        )

        val indexNames = buildSet {
            migrated.query("PRAGMA index_list(notification_events)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        for (expected in EXPECTED_V6_INDEXES) {
            assertTrue("missing index $expected, found $indexNames", expected in indexNames)
        }
        migrated.close()
    }

    @Test
    fun migrationFiveToSixKeepsPersonalizedModelWeights() {
        // The learned model is the one artifact a user cannot regenerate by waiting.
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO personalized_model
                    (id, weights, bias, positiveCount, negativeCount, updatedAt, version,
                     evaluationCount, personalCorrectCount, baselineCorrectCount,
                     importantEvaluationCount, importantCorrectCount,
                     notImportantEvaluationCount, falseImportantCount)
                VALUES (1, X'0102030405060708', 0.5, 12, 9, 1000, 1, 40, 30, 28, 10, 9, 8, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AttentionDatabase.MIGRATION_5_6,
        )
        migrated.query("SELECT positiveCount, negativeCount, bias FROM personalized_model").use {
            assertTrue("model row should survive", it.moveToFirst())
            assertEquals(12, it.getInt(0))
            assertEquals(9, it.getInt(1))
            assertEquals(0.5f, it.getFloat(2), 0.0001f)
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        val ALL_MIGRATIONS = arrayOf(
            AttentionDatabase.MIGRATION_1_2,
            AttentionDatabase.MIGRATION_2_3,
            AttentionDatabase.MIGRATION_3_4,
            AttentionDatabase.MIGRATION_4_5,
            AttentionDatabase.MIGRATION_5_6,
        )

        val EXPECTED_V6_INDEXES = listOf(
            "index_notification_events_priority",
            "index_notification_events_queued",
            "index_notification_events_action_postedAt",
        )
    }
}
