package com.attentionos.domain

/**
 * The single source of truth for how a score becomes a priority, and for which alerts are
 * protected from suppression.
 *
 * Both rules were previously duplicated: the score ladder existed in [PriorityEngine] and again
 * in the personalization policy, and the protected-category set existed in [PriorityEngine] and
 * again in the repository. Divergence between copies would have silently changed safety
 * behaviour, so they live here and are used everywhere.
 */
object AttentionPolicy {

    /** Score thresholds for each priority band, highest first. */
    private const val CRITICAL_THRESHOLD = 0.86f
    private const val HIGH_THRESHOLD = 0.68f
    private const val MEDIUM_THRESHOLD = 0.46f
    private const val LOW_THRESHOLD = 0.24f

    /** Urgency at or above this is treated as strong enough to bypass suppression. */
    const val STRONG_URGENCY_THRESHOLD = 0.75f

    /**
     * Categories that must never be suppressed by focus mode, quiet hours, or personalization.
     */
    val neverSuppressCategories: Set<NotificationCategory> = setOf(
        NotificationCategory.SECURITY,
        NotificationCategory.FINANCE,
    )

    /** Priorities that focus mode may hold back for later review. */
    val queueablePriorities: Set<AttentionPriority> = setOf(
        AttentionPriority.LOW,
        AttentionPriority.SILENT,
    )

    /**
     * The band Attention Mode holds back, which is wider than the band held when it is off.
     *
     * This is how the mode does its job. It previously worked by subtracting 0.17 from every
     * non-urgent score so that MEDIUM items fell into [queueablePriorities] — which both
     * reclassified notifications because of a user setting, and made the HIGH threshold
     * arithmetically unreachable for a message from an unknown sender: the ceiling for such a
     * message is 0.850, the penalty took it to 0.680, and HIGH begins at 0.680. No model output
     * could clear it. Widening the queue band achieves the same restraint without capping the
     * top of the scale.
     */
    val focusModeQueueablePriorities: Set<AttentionPriority> = setOf(
        AttentionPriority.MEDIUM,
        AttentionPriority.LOW,
        AttentionPriority.SILENT,
    )

    /** Notification category hints that always warrant immediate attention. */
    const val CATEGORY_HINT_CALL = "call"
    const val CATEGORY_HINT_ALARM = "alarm"

    /**
     * A score standing in for a band, so stored history and the personal model keep a number.
     *
     * The engine classifies into bands now, but the database column, the JSONL export and the
     * personal classifier's feature vector all predate that and read a float. Midpoints keep
     * those consistent without reviving the weighted sum that produced them.
     */
    fun representativeScore(priority: AttentionPriority): Float = when (priority) {
        AttentionPriority.CRITICAL -> 0.93f
        AttentionPriority.HIGH -> 0.77f
        AttentionPriority.MEDIUM -> 0.57f
        AttentionPriority.LOW -> 0.35f
        AttentionPriority.SILENT -> 0.12f
    }

    fun priorityFor(score: Float): AttentionPriority = when {
        score >= CRITICAL_THRESHOLD -> AttentionPriority.CRITICAL
        score >= HIGH_THRESHOLD -> AttentionPriority.HIGH
        score >= MEDIUM_THRESHOLD -> AttentionPriority.MEDIUM
        score >= LOW_THRESHOLD -> AttentionPriority.LOW
        else -> AttentionPriority.SILENT
    }

    /**
     * True when this alert must keep its prominence regardless of user history or the
     * personalized model. Personalization may raise such an alert but never lower it.
     */
    fun isSafetyProtected(
        category: NotificationCategory,
        semanticUrgency: Float,
        categoryHint: String?,
    ): Boolean = category in neverSuppressCategories ||
        semanticUrgency >= STRONG_URGENCY_THRESHOLD ||
        categoryHint == CATEGORY_HINT_CALL ||
        categoryHint == CATEGORY_HINT_ALARM

    fun shouldQueue(focusModeEnabled: Boolean, priority: AttentionPriority): Boolean =
        focusModeEnabled && priority in focusModeQueueablePriorities
}
