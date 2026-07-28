package com.attentionos.data.local

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

    @Query("SELECT * FROM notification_events ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 60): Flow<List<NotificationEventEntity>>

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

    @Query(
        """
        SELECT AVG(analysisDurationMillis) FROM notification_events
        WHERE analysisDurationMillis > 0
        """,
    )
    fun observeAverageAnalysisMillis(): Flow<Double?>

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

    @Query("DELETE FROM training_examples WHERE createdAt < :before AND exported = 1")
    suspend fun deleteExportedTrainingBefore(before: Long): Int

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
