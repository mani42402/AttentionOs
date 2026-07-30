package com.attentionos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AttentionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: NotificationEventEntity): Long

    /**
     * Recent events as a list projection.
     *
     * Selects only the columns the UI renders; notably it reports whether an embedding exists
     * rather than loading the blob itself, which the list never displays.
     */
    @Query(
        """
        SELECT id, notificationKey, appLabel, title, message, postedAt, priority, category,
               explanation, queued, action, personalProbability, personalModelApplied,
               (embeddingQ8 IS NOT NULL) AS hasEmbedding
        FROM notification_events
        ORDER BY postedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int = 60): Flow<List<NotificationListItem>>

    @Query(
        """
        SELECT * FROM notification_events
        WHERE priority IN ('CRITICAL', 'HIGH')
        ORDER BY postedAt DESC LIMIT :limit
        """,
    )
    fun observeImportant(limit: Int = 60): Flow<List<NotificationEventEntity>>

    @Query("SELECT * FROM notification_events WHERE queued = 1 ORDER BY postedAt DESC LIMIT :limit")
    fun observeQueued(limit: Int = 60): Flow<List<NotificationEventEntity>>

    @Query("SELECT * FROM notification_events WHERE notificationKey = :key LIMIT 1")
    suspend fun eventByKey(key: String): NotificationEventEntity?

    @Query(
        """
        UPDATE notification_events
        SET action = :action, actedAt = :actedAt
        WHERE notificationKey = :key
        """,
    )
    suspend fun markAction(key: String, action: String, actedAt: Long)

    @Query("SELECT * FROM user_memory WHERE senderHash = :senderHash LIMIT 1")
    suspend fun memory(senderHash: String): UserMemoryEntity?

    @Upsert
    suspend fun upsertMemory(memory: UserMemoryEntity)

    @Insert
    suspend fun insertTrainingExample(example: TrainingExampleEntity)

    @Query("DELETE FROM training_examples WHERE sourceEventId = :sourceEventId")
    suspend fun deleteTrainingForSource(sourceEventId: Long)

    @Query("SELECT COUNT(*) FROM training_examples")
    fun observeTrainingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events")
    fun observeEventCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_memory")
    fun observeSenderCount(): Flow<Int>

    /** How many stored rows actually hold notification text, i.e. the opt-in content. */
    @Query("SELECT COUNT(*) FROM notification_events WHERE title IS NOT NULL OR message IS NOT NULL")
    fun observeStoredContentCount(): Flow<Int>

    @Query("SELECT MIN(postedAt) FROM notification_events")
    fun observeOldestEventAt(): Flow<Long?>

    /**
     * Recent inference latency.
     *
     * Bounded by [since] so this is an index-assisted range scan rather than a full-table
     * average. It is observed by the UI and therefore re-runs on every notification insert,
     * which made an unbounded scan over the full retention window the most expensive query
     * in the app.
     */
    @Query(
        """
        SELECT AVG(analysisDurationMillis) FROM notification_events
        WHERE analysisDurationMillis > 0 AND postedAt >= :since
        """,
    )
    fun observeAverageAnalysisMillis(since: Long): Flow<Double?>

    /**
     * Corrected events that still carry an embedding from the current encoder.
     *
     * The replay buffer for refitting. Filtering on languageModelVersion matters: embeddings
     * from a previous encoder describe a different space, and mixing them in would train the
     * classifier on incompatible features.
     */
    @Query(
        """
        SELECT * FROM notification_events
        WHERE action IN ('IMPORTANT', 'NOT_IMPORTANT')
          AND embeddingQ8 IS NOT NULL
          AND languageModelVersion = :modelVersion
        ORDER BY actedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun correctedEvents(modelVersion: String, limit: Int = 2_000): List<NotificationEventEntity>

    @Query("SELECT * FROM training_examples WHERE exported = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun trainingForExport(limit: Int = 10_000): List<TrainingExampleEntity>

    @Query("UPDATE training_examples SET exported = 1 WHERE id IN (:ids)")
    suspend fun markTrainingExported(ids: List<Long>)

    @Query("SELECT * FROM personalized_model WHERE id = 1 LIMIT 1")
    suspend fun personalizedModel(): PersonalizedModelEntity?

    @Query("SELECT * FROM personalized_model WHERE id = 1 LIMIT 1")
    fun observePersonalizedModel(): Flow<PersonalizedModelEntity?>

    @Upsert
    suspend fun upsertPersonalizedModel(model: PersonalizedModelEntity)

    @Query("DELETE FROM personalized_model")
    suspend fun deletePersonalizedModel()

    @Query(
        """
        SELECT COUNT(*) FROM notification_events
        WHERE postedAt >= :since
        """,
    )
    fun observeReceivedSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM notification_events
        WHERE postedAt >= :since AND priority IN ('CRITICAL', 'HIGH')
        """,
    )
    fun observeImportantSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM notification_events
        WHERE postedAt >= :since AND queued = 1
        """,
    )
    fun observeQueuedSince(since: Long): Flow<Int>

    @Query("DELETE FROM notification_events WHERE postedAt < :before")
    suspend fun deleteEventsBefore(before: Long): Int

    /**
     * Deletes aged training rows regardless of export status.
     *
     * This previously required `exported = 1`, so an unexported example — which carries a
     * sender hash and a content embedding — was never removed by retention and accumulated
     * for the life of the install.
     */
    @Query("DELETE FROM training_examples WHERE createdAt < :before")
    suspend fun deleteTrainingBefore(before: Long): Int

    /** Caps total training rows, keeping the most recent. Guards against unbounded growth. */
    @Query(
        """
        DELETE FROM training_examples WHERE id NOT IN (
            SELECT id FROM training_examples ORDER BY createdAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTrainingTo(keep: Int): Int

    /** Sender memory for senders not seen within the retention horizon. */
    @Query("DELETE FROM user_memory WHERE updatedAt < :before")
    suspend fun deleteMemoryBefore(before: Long): Int

    /** Caps sender memory rows, keeping the most recently updated. */
    @Query(
        """
        DELETE FROM user_memory WHERE senderHash NOT IN (
            SELECT senderHash FROM user_memory ORDER BY updatedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimMemoryTo(keep: Int): Int

    @Query("DELETE FROM notification_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM user_memory")
    suspend fun deleteAllMemory()

    @Query("DELETE FROM training_examples")
    suspend fun deleteAllTraining()

    @Transaction
    suspend fun deleteAllUserData() {
        deleteAllEvents()
        deleteAllMemory()
        deleteAllTraining()
        deletePersonalizedModel()
    }
}
