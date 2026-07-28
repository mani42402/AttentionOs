package com.attentionos.data.local

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
    version = 5,
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
    }
}
