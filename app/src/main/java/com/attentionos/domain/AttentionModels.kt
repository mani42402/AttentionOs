package com.attentionos.domain

enum class AttentionPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    SILENT,
}

enum class NotificationCategory {
    SECURITY,
    FINANCE,
    WORK,
    SOCIAL,
    DELIVERY,
    PROMOTION,
    SYSTEM,
    OTHER,
}

data class NotificationSignal(
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val isConversation: Boolean,
    val isOngoing: Boolean,
    val categoryHint: String?,
)

data class UserMemory(
    val senderHash: String,
    val importanceScore: Float = 0.5f,
    val openRate: Float = 0.5f,
    val averageResponseSeconds: Long = 600L,
    val interactionCount: Int = 0,
)

data class AttentionContext(
    val focusModeEnabled: Boolean,
    val hourOfDay: Int,
)

data class AttentionDecision(
    val priority: AttentionPriority,
    val category: NotificationCategory,
    val score: Float,
    val explanation: String,
    val shouldQueue: Boolean,
    val semanticEmbedding: FloatArray? = null,
    val languageModelVersion: String? = null,
    val semanticUrgency: Float = 0f,
)
