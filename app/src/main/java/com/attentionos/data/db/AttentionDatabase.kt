package com.attentionos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationEventEntity::class,
        UserMemoryEntity::class,
        TrainingExampleEntity::class,
        PersonalizedModelEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun attentionDao(): AttentionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN embeddingQ8 BLOB",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN languageModelVersion TEXT",
                )
                database.execSQL(
                    "ALTER TABLE training_examples ADD COLUMN embeddingQ8 BLOB",
                )
                database.execSQL(
                    "ALTER TABLE training_examples ADD COLUMN languageModelVersion TEXT",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN contextHour INTEGER NOT NULL DEFAULT 12",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN focusModeAtDecision INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN senderImportanceAtDecision REAL NOT NULL DEFAULT 0.5",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN senderOpenRateAtDecision REAL NOT NULL DEFAULT 0.5",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN baseScoreAtDecision REAL NOT NULL DEFAULT 0.5",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN semanticUrgency REAL NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS personalized_model (
                        id INTEGER NOT NULL,
                        weights BLOB NOT NULL,
                        bias REAL NOT NULL,
                        positiveCount INTEGER NOT NULL,
                        negativeCount INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        version INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN personalProbability REAL",
                )
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN personalModelApplied INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN evaluationCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN personalCorrectCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN baselineCorrectCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN importantEvaluationCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN importantCorrectCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN notImportantEvaluationCount INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN falseImportantCount INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN analysisDurationMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds indexes for the columns the UI filters on.
         *
         * Every insert invalidates the observed queries, so the important/queued/review lists
         * re-ran as full scans over the whole retention window on each incoming notification.
         */
        /**
         * Purges every identity derived from the old unsalted hash.
         *
         * The previous scheme was an unkeyed SHA-256 of `package:title`, which is reversible by
         * enumerating installed packages against common contact names. The values cannot be
         * re-derived under the new keyed scheme — titles are not stored unless the user opts in
         * — and keeping them would leave the reversible identifiers sitting in the database, so
         * they are cleared and relearned.
         *
         * What this costs the user: sender importance re-converges within a handful of
         * interactions, and training examples only feed the JSONL export. The personalized
         * model's learned weights are NOT touched.
         */
        /**
         * Adds class centroids to the personalized model.
         *
         * A nearest-class-mean is usable from a handful of corrections, where the logistic
         * classifier is still noise, so this is what lets personalization mean anything for
         * users who never reach the 50-correction bar.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN importantCentroid BLOB",
                )
                database.execSQL(
                    "ALTER TABLE personalized_model ADD COLUMN notImportantCentroid BLOB",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM user_memory")
                database.execSQL("UPDATE notification_events SET senderHash = ''")
                database.execSQL("DELETE FROM training_examples")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_events_priority " +
                        "ON notification_events (priority)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_events_queued " +
                        "ON notification_events (queued)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_events_action_postedAt " +
                        "ON notification_events (action, postedAt)",
                )
            }
        }
    }
}
