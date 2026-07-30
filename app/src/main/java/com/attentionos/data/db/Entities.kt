package com.attentionos.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    indices = [
        Index("notificationKey", unique = true),
        Index("postedAt"),
        Index("senderHash"),
        Index("priority"),
        Index("queued"),
        Index(value = ["action", "postedAt"]),
    ],
)
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val appLabel: String,
    val senderHash: String,
    val title: String?,
    val message: String?,
    val postedAt: Long,
    val priority: String,
    val category: String,
    val score: Float,
    val explanation: String,
    val queued: Boolean,
    val action: String? = null,
    val actedAt: Long? = null,
    val embeddingQ8: ByteArray? = null,
    val languageModelVersion: String? = null,
    val contextHour: Int = 12,
    val focusModeAtDecision: Boolean = false,
    val senderImportanceAtDecision: Float = 0.5f,
    val senderOpenRateAtDecision: Float = 0.5f,
    val baseScoreAtDecision: Float = 0.5f,
    val semanticUrgency: Float = 0f,
    val personalProbability: Float? = null,
    val personalModelApplied: Boolean = false,
    val analysisDurationMillis: Long = 0L,
)

@Entity(tableName = "user_memory")
data class UserMemoryEntity(
    @PrimaryKey val senderHash: String,
    val importanceScore: Float = 0.5f,
    val averageResponseSeconds: Long = 600L,
    val openCount: Int = 0,
    val dismissCount: Int = 0,
    val interactionCount: Int = 0,
    val updatedAt: Long,
)

@Entity(
    tableName = "training_examples",
    indices = [Index("createdAt")],
)
data class TrainingExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceEventId: Long,
    val featuresJson: String,
    val expectedPriority: String,
    val createdAt: Long,
    val exported: Boolean = false,
    val embeddingQ8: ByteArray? = null,
    val languageModelVersion: String? = null,
)

@Entity(tableName = "personalized_model")
data class PersonalizedModelEntity(
    @PrimaryKey val id: Int = 1,
    val weights: ByteArray,
    val bias: Float,
    val positiveCount: Int,
    val negativeCount: Int,
    val updatedAt: Long,
    val version: Int,
    val evaluationCount: Int = 0,
    val personalCorrectCount: Int = 0,
    val baselineCorrectCount: Int = 0,
    val importantEvaluationCount: Int = 0,
    val importantCorrectCount: Int = 0,
    val notImportantEvaluationCount: Int = 0,
    val falseImportantCount: Int = 0,
    /** Class means over embeddings, used before the classifier has enough data. */
    val importantCentroid: ByteArray? = null,
    val notImportantCentroid: ByteArray? = null,
    /** Platt scaling fitted at the last refit; identity until there are enough corrections. */
    val calibrationSlope: Float = 1f,
    val calibrationIntercept: Float = 0f,
)
