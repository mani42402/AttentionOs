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
        var score = 0.28f

        score += analysis.urgency * 0.34f
        score += (memory?.importanceScore ?: 0.5f) * 0.20f
        score += (memory?.openRate ?: 0.5f) * 0.10f
        if (signal.isConversation) score += 0.08f
        if (analysis.category == NotificationCategory.SECURITY) score += 0.28f
        if (analysis.category == NotificationCategory.FINANCE) score += 0.13f
        if (analysis.category == NotificationCategory.PROMOTION) score -= 0.32f
        if (signal.isOngoing) score -= 0.18f

        val quietHours = context.hourOfDay < QUIET_HOURS_END || context.hourOfDay >= QUIET_HOURS_START
        val isCall = signal.categoryHint == AttentionPolicy.CATEGORY_HINT_CALL
        val isAlarm = signal.categoryHint == AttentionPolicy.CATEGORY_HINT_ALARM
        val hasStrongUrgency =
            analysis.urgency >= AttentionPolicy.STRONG_URGENCY_THRESHOLD || isCall || isAlarm
        if (
            quietHours &&
            !hasStrongUrgency &&
            analysis.category !in AttentionPolicy.neverSuppressCategories
        ) {
            score -= 0.10f
        }
        if (
            context.focusModeEnabled &&
            !hasStrongUrgency &&
            analysis.category !in AttentionPolicy.neverSuppressCategories
        ) {
            score -= 0.17f
        }

        // Hard floors are independent of model confidence and user history.
        score = when {
            isCall -> maxOf(score, 0.90f)
            analysis.category == NotificationCategory.SECURITY -> maxOf(score, 0.88f)
            isAlarm -> maxOf(score, 0.78f)
            analysis.category == NotificationCategory.FINANCE -> maxOf(score, 0.70f)
            else -> score
        }.coerceIn(0f, 1f)
        val priority = AttentionPolicy.priorityFor(score)
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

        return LanguageAnalysis(urgency.coerceIn(0f, 1f), category)
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
