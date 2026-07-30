package com.attentionos.data.db

/**
 * Projection of [NotificationEventEntity] for list rendering.
 *
 * The UI reads twelve of the entity's twenty-five columns and never renders the embedding, yet
 * observing the full row pulled a 384-byte blob per record — roughly 23KB per emission for a
 * 60-row list, re-read on every insert. The blob also gave the entity identity-based equality
 * (arrays compare by reference), so Compose could never skip a recomposition.
 *
 * Only whether an embedding exists matters to the UI: an event without one cannot be used as a
 * training example, so it is not offered for review.
 */
data class NotificationListItem(
    val id: Long,
    val notificationKey: String,
    val appLabel: String,
    val title: String?,
    val message: String?,
    val postedAt: Long,
    val priority: String,
    val category: String,
    val explanation: String,
    val queued: Boolean,
    val action: String?,
    val hasEmbedding: Boolean,
    /** Personal-model estimate shown in the review explanation, null before the model exists. */
    val personalProbability: Float?,
    /** Whether that estimate actually influenced the decision, or was only recorded. */
    val personalModelApplied: Boolean,
)
