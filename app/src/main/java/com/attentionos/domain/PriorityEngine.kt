package com.attentionos.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Deterministic scoring stage that turns a notification into an [AttentionDecision].
 *
 * The engine itself is allocation-light, but the injected [LanguageAnalyzer] is not: in
 * production it is the ONNX-backed analyzer, which performs file I/O on first use and a
 * transformer forward pass per call. Callers must therefore treat [decide] as blocking work and
 * keep it off the main thread. [KeywordLanguageAnalyzer] is the always-available fallback used
 * when the model is unavailable, and in tests.
 *
 * Score bands and protected categories come from [AttentionPolicy] so that scoring here and
 * personalized re-scoring elsewhere can never diverge.
 */
class PriorityEngine(
    private val languageAnalyzer: LanguageAnalyzer = KeywordLanguageAnalyzer(),
) {
    fun decide(
        signal: NotificationSignal,
        context: AttentionContext,
        memory: UserMemory?,
    ): AttentionDecision {
        val analysis = languageAnalyzer.analyze(signal.title, signal.text, signal.packageName)

        val isCall = signal.categoryHint == AttentionPolicy.CATEGORY_HINT_CALL
        val isAlarm = signal.categoryHint == AttentionPolicy.CATEGORY_HINT_ALARM
        val hasStrongUrgency =
            analysis.urgency >= AttentionPolicy.STRONG_URGENCY_THRESHOLD || isCall || isAlarm

        // The nearest description decides. Everything after this point may only *raise* the
        // result — the deterministic floors because they are promises, and a sender the user
        // actually engages with because who sent something outranks what it says.
        var priority = analysis.band?.priority ?: AttentionPolicy.priorityFor(fallbackScore(analysis, memory, signal))

        // Who this is to the user. A person whose messages get opened matters whatever the words
        // are, in any language, however novel the phrasing — the one signal that needs no
        // description and cannot be out-of-vocabulary. It was previously worth 0.08 in a sum.
        if (memory != null && memory.interactionCount >= KNOWN_SENDER_INTERACTIONS &&
            memory.openRate >= KNOWN_SENDER_OPEN_RATE
        ) {
            priority = priority.raisedByOneBand()
        }

        // Hard floors, independent of the model and of user history.
        priority = when {
            isCall -> AttentionPriority.CRITICAL
            analysis.deterministicCategory == NotificationCategory.SECURITY ->
                AttentionPriority.CRITICAL
            isAlarm -> maxPriority(priority, AttentionPriority.HIGH)
            analysis.deterministicCategory == NotificationCategory.FINANCE ->
                maxPriority(priority, AttentionPriority.HIGH)
            else -> priority
        }

        // An ongoing notification is a progress bar, not an event.
        if (signal.isOngoing && !hasStrongUrgency &&
            analysis.category !in AttentionPolicy.neverSuppressCategories
        ) {
            priority = priority.loweredByOneBand()
        }

        val score = AttentionPolicy.representativeScore(priority)
        val shouldQueue = AttentionPolicy.shouldQueue(context.focusModeEnabled, priority)

        return AttentionDecision(
            priority = priority,
            category = analysis.category,
            score = score,
            explanation = explanationFor(priority, analysis, memory, context),
            shouldQueue = shouldQueue,
            semanticEmbedding = analysis.semanticEmbedding,
            languageModelVersion = analysis.modelVersion,
            semanticUrgency = analysis.urgency,
        )
    }

    /** One band more prominent, saturating at the top. */
    private fun AttentionPriority.raisedByOneBand(): AttentionPriority =
        AttentionPriority.entries[(ordinal - 1).coerceAtLeast(0)]

    /** One band quieter, saturating at the bottom. */
    private fun AttentionPriority.loweredByOneBand(): AttentionPriority =
        AttentionPriority.entries[(ordinal + 1).coerceAtMost(AttentionPriority.entries.lastIndex)]

    private fun maxPriority(a: AttentionPriority, b: AttentionPriority): AttentionPriority =
        if (a.ordinal <= b.ordinal) a else b

    /**
     * Used only when no model ran — a corrupt asset, or the keyword analyzer standing alone.
     *
     * Deliberately crude. It exists so the app still ranks something rather than treating every
     * notification identically, and it is not the path any real decision takes.
     */
    private fun fallbackScore(
        analysis: LanguageAnalysis,
        memory: UserMemory?,
        signal: NotificationSignal,
    ): Float {
        var score = 0.30f + analysis.urgency * 0.45f
        if (signal.isConversation) score += 0.10f
        // Regression guard: the keyword analyzer is the only thing running on this path, and it
        // can still recognise a promotion. Dropping that demotion let a "save 40% today" reach
        // LOW instead of SILENT, which is the difference between held and not held.
        if (analysis.category == NotificationCategory.PROMOTION) score -= 0.30f
        memory?.let { score += (it.importanceScore - 0.5f) * 0.20f }
        return score.coerceIn(0f, 1f)
    }

    private fun explanationFor(
        priority: AttentionPriority,
        analysis: LanguageAnalysis,
        memory: UserMemory?,
        context: AttentionContext,
    ): String = when {
        analysis.category == NotificationCategory.SECURITY ->
            "Security alerts are always allowed through."
        priority == AttentionPriority.CRITICAL ->
            "Urgent language and your interaction history indicate this needs attention."
        memory != null && memory.interactionCount >= 3 && memory.openRate >= 0.7f ->
            "You usually open notifications from this sender quickly."
        context.focusModeEnabled && priority <= AttentionPriority.LOW ->
            "Recommended for quiet delivery; the original notification stays visible."
        analysis.category == NotificationCategory.PROMOTION ->
            "A promotional update that can wait for your next check-in."
        else ->
            "${analysis.category.readableName()} signal · ${(analysis.urgency * 100).roundToInt()}% urgency."
    }

    private companion object {
        /**
         * What counts as a sender the user actually engages with.
         *
         * Three interactions is enough to distinguish a person from an app, and a 70% open rate
         * says the user reads them. Below that the app has no evidence and does not guess.
         */
        const val KNOWN_SENDER_INTERACTIONS = 3
        const val KNOWN_SENDER_OPEN_RATE = 0.7f

        /** Quiet hours run from [QUIET_HOURS_START] until [QUIET_HOURS_END] local time. */
        const val QUIET_HOURS_START = 23
        const val QUIET_HOURS_END = 7
    }
}

interface LanguageAnalyzer {
    fun analyze(title: String?, text: String?, packageName: String): LanguageAnalysis
}

data class LanguageAnalysis(
    val urgency: Float,
    val category: NotificationCategory,
    val semanticEmbedding: FloatArray? = null,
    val modelVersion: String? = null,
    /**
     * The band the nearest description argues for, or null when no model ran.
     *
     * This is the decision. When it is present the engine uses it directly instead of adding up
     * weighted terms; [urgency] survives only because the safety-floor check and the stored
     * history both read it.
     */
    val band: AttentionDescriptions.Band? = null,
    /**
     * Category established by deterministic rules, as opposed to [category] which may be the
     * model's nearest guess.
     *
     * Only this one may trigger a safety floor. Nearest-prototype always returns *something*, and
     * two categories carry hard floors, so letting a guess drive them promoted a third of the
     * noise in the corpus to HIGH — a delivery update matching the finance prototype better than
     * anything else was enough. A floor is a guarantee and has to rest on evidence.
     */
    val deterministicCategory: NotificationCategory? = null,
)

class KeywordLanguageAnalyzer : LanguageAnalyzer {
    override fun analyze(title: String?, text: String?, packageName: String): LanguageAnalysis {
        val content = buildString((title?.length ?: 0) + (text?.length ?: 0) + 1) {
            title?.let(::append)
            append(' ')
            text?.let(::append)
        }.lowercase(Locale.ROOT)
        val packageId = packageName.lowercase(Locale.ROOT)

        val category = when {
            securityWords.any(content::contains) -> NotificationCategory.SECURITY
            financeWords.any(content::contains) || financeApps.any(packageId::contains) ->
                NotificationCategory.FINANCE
            socialApps.any(packageId::contains) -> NotificationCategory.SOCIAL
            workWords.any(content::contains) || workApps.any(packageId::contains) ->
                NotificationCategory.WORK
            deliveryWords.any(content::contains) -> NotificationCategory.DELIVERY
            promoWords.any(content::contains) -> NotificationCategory.PROMOTION
            packageId.startsWith("android") || packageId.contains("systemui") ->
                NotificationCategory.SYSTEM
            else -> NotificationCategory.OTHER
        }

        var urgency = 0.12f
        urgency += urgentWords.count(content::contains).coerceAtMost(3) * 0.25f
        if ('?' in content) urgency += 0.06f
        if (securityWords.any(content::contains)) urgency += 0.55f
        if (content.contains("missed call") || content.contains("incoming call")) urgency += 0.32f
        if (promoWords.any(content::contains)) urgency -= 0.10f
        val strongActionPhrase = content.contains("action required") &&
            (content.contains("immediately") || content.contains(" right now"))
        val productionIncident = content.contains("production") &&
            (
                content.contains(" is down") ||
                    content.contains("incident") ||
                    content.contains("outage")
                )
        if (strongActionPhrase || productionIncident) urgency = maxOf(urgency, 0.82f)

        // A keyword match is evidence, not a guess, so it is allowed to carry a safety floor.
        return LanguageAnalysis(
            urgency = urgency.coerceIn(0f, 1f),
            category = category,
            deterministicCategory = category,
        )
    }

    private companion object {
        val urgentWords = arrayOf(
            "urgent", "asap", "immediately", "emergency", "critical", "now",
            "production down", "failed", "action required", "deadline",
        )
        val securityWords = arrayOf(
            "verification code", "security code", "otp", "one-time password",
            "new login", "suspicious", "fraud", "account locked",
        )
        val financeWords = arrayOf(
            "payment", "transaction", "debited", "credited", "bank", "invoice",
        )
        val workWords = arrayOf(
            "meeting", "production", "client", "review", "task", "project", "deadline",
        )
        val deliveryWords = arrayOf(
            "delivered", "delivery", "courier", "arriving", "driver", "order",
        )
        val promoWords = arrayOf(
            "sale", "discount", "offer", "deal", "coupon", "save up to", "recommended for you",
        )
        val financeApps = arrayOf("bank", "wallet", "paypal", "wise", "revolut")
        val workApps = arrayOf("slack", "teams", "outlook", "linear", "asana")
        val socialApps = arrayOf("whatsapp", "telegram", "signal", "instagram", "messenger")
    }
}

fun NotificationCategory.readableName(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)
