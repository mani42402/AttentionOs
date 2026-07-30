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
    /**
     * Stable identity of the person or thread this came from, when the posting app supplies
     * one. Falls back to the title, which is unreliable: apps that put a subject line, an
     * unread count or a timestamp there produce a different "sender" on every notification,
     * fragmenting the learned history for that contact.
     */
    val conversationId: String? = null,
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
