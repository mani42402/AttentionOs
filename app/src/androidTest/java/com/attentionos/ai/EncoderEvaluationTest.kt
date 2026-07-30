package com.attentionos.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionPolicy
import com.attentionos.domain.NotificationCategory
import com.attentionos.domain.NotificationSignal
import com.attentionos.domain.PriorityEngine
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scored evaluation of the on-device encoder against a labelled notification set.
 *
 * This exists so encoder choices are settled with numbers rather than intuition: the same
 * harness scores whichever model is wired in, so swapping one and re-running produces a
 * comparable scorecard. It is the measurement half of the planned static-embedding bake-off.
 *
 * Two properties are asserted rather than merely reported, because regressing either would be a
 * user-visible safety failure:
 *  - every notification that must never be silenced is ranked prominently, and
 *  - per-notification latency stays inside the budget the battery story depends on.
 *
 * The remaining figures are logged for comparison between encoders.
 */
@RunWith(AndroidJUnit4::class)
class EncoderEvaluationTest {

    private data class Sample(
        val app: String,
        val title: String,
        val body: String,
        val category: NotificationCategory,
        /**
         * True when the engine *guarantees* prominence via a hard floor: the never-suppress
         * categories plus calls and alarms. These are asserted.
         */
        val guaranteed: Boolean,
        /**
         * True when a reasonable user would want this promptly, whether or not a rule
         * guarantees it. Measured and reported, not asserted — work urgency is meant to be
         * learned from corrections rather than hardcoded, so failing here is a quality signal
         * for personalization, not a regression.
         */
        val shouldRankHigh: Boolean = guaranteed,
        val isConversation: Boolean = false,
        val categoryHint: String? = null,
    )

    /**
     * Scores the shipped encoder.
     *
     * This ran against `all-MiniLM-L6-v2` as well during the Phase 2 bake-off; the transformer
     * lost and was deleted, so only the survivor is scored now. The recorded numbers are in
     * docs/MODEL_STRATEGY.md — re-add a second candidate here to repeat the comparison.
     */
    @Test
    fun scoreShippedEncoder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        score(StaticEmbeddingAnalyzer.modelVersion(), analyzer)
    }

    private fun score(modelName: String, analyzer: com.attentionos.domain.LanguageAnalyzer) {
        val engine = PriorityEngine(analyzer)

        var categoryHits = 0
        var guaranteedTotal = 0
        var guaranteedRanked = 0
        var wantedTotal = 0
        var wantedRanked = 0
        var quietTotal = 0
        var quietCorrect = 0
        val latenciesMicros = mutableListOf<Long>()
        val failures = mutableListOf<String>()
        val softMisses = mutableListOf<String>()
        val categoryMisses = mutableListOf<String>()

        for (sample in SAMPLES) {
            val signal = NotificationSignal(
                packageName = sample.app,
                title = sample.title,
                text = sample.body,
                postedAt = System.currentTimeMillis(),
                isConversation = sample.isConversation,
                isOngoing = false,
                categoryHint = sample.categoryHint,
            )
            lateinit var decision: com.attentionos.domain.AttentionDecision
            val nanos = measureNanoTime {
                decision = engine.decide(
                    signal = signal,
                    context = AttentionContext(focusModeEnabled = false, hourOfDay = 14),
                    memory = null,
                )
            }
            latenciesMicros += nanos / 1_000

            if (decision.category == sample.category) {
                categoryHits++
            } else {
                categoryMisses += "${sample.title} -> ${decision.category} (want ${sample.category})"
            }

            val ranked = decision.priority.ordinal <= HIGH_ORDINAL
            if (sample.guaranteed) {
                guaranteedTotal++
                if (ranked) {
                    guaranteedRanked++
                } else {
                    failures += "guaranteed alert was silenced: ${sample.title} -> ${decision.priority}"
                }
            }
            if (sample.shouldRankHigh) {
                wantedTotal++
                if (ranked) wantedRanked++ else softMisses += "${sample.title} -> ${decision.priority}"
            } else {
                quietTotal++
                if (!ranked) quietCorrect++
            }
        }

        val sorted = latenciesMicros.sorted()
        val median = sorted[sorted.size / 2] / 1000.0
        val p90 = sorted[(sorted.size * 9) / 10] / 1000.0
        val categoryAccuracy = categoryHits.toDouble() / SAMPLES.size
        val guaranteedRecall = guaranteedRanked.toDouble() / guaranteedTotal
        val wantedRecall = wantedRanked.toDouble() / wantedTotal
        val quietPrecision = quietCorrect.toDouble() / quietTotal

        Log.i(
            TAG,
            """
            |
            |=== encoder scorecard =========================================
            |  model              $modelName
            |  samples            ${SAMPLES.size}
            |  category accuracy  ${"%.1f".format(categoryAccuracy * 100)}%
            |  guaranteed alerts  ${"%.1f".format(guaranteedRecall * 100)}%  ($guaranteedRanked/$guaranteedTotal)   [asserted]
            |  wanted promptly    ${"%.1f".format(wantedRecall * 100)}%  ($wantedRanked/$wantedTotal)   [measured]
            |  correctly quiet    ${"%.1f".format(quietPrecision * 100)}%  ($quietCorrect/$quietTotal)
            |  ranked low but wanted: ${if (softMisses.isEmpty()) "none" else softMisses.joinToString("; ")}
            |  category misses:   ${if (categoryMisses.isEmpty()) "none" else categoryMisses.joinToString("\n                     ")}
            |  latency median     ${"%.1f".format(median)} ms
            |  latency p90        ${"%.1f".format(p90)} ms
            |===============================================================
            """.trimMargin(),
        )

        // Safety: the hard floors are a documented guarantee, so a miss here is a regression.
        assertTrue(
            "alerts covered by a safety floor were ranked low: $failures",
            failures.isEmpty(),
        )
        // Budget: the no-foreground-service, no-wake-lock battery story depends on this.
        assertTrue(
            "p90 latency ${"%.1f".format(p90)}ms exceeds the ${LATENCY_BUDGET_MS}ms budget",
            p90 <= LATENCY_BUDGET_MS,
        )
    }

    private companion object {
        const val TAG = "AttentionEval"
        const val LATENCY_BUDGET_MS = 60.0
        val HIGH_ORDINAL = com.attentionos.domain.AttentionPriority.HIGH.ordinal

        /**
         * Labelled notifications spanning the categories the engine distinguishes.
         *
         * Deliberately includes phrasing that keyword rules alone get wrong — an urgent message
         * with no urgent keyword, a promotion using words that appear in security alerts — so
         * the score reflects what the encoder adds rather than what the keyword list already
         * catches.
         */
        val SAMPLES = listOf(
            Sample("com.bank.app", "Security alert", "Unusual sign-in from a new device", NotificationCategory.SECURITY, guaranteed = true),
            Sample("com.auth.app", "Your code", "Verification code 448291 expires in 5 minutes", NotificationCategory.SECURITY, guaranteed = true),
            Sample("com.bank.app", "Card charged", "A payment of \$482.10 was debited from your account", NotificationCategory.FINANCE, guaranteed = true),
            Sample("com.bank.app", "Low balance", "Your account balance has fallen below \$50", NotificationCategory.FINANCE, guaranteed = true),
            // Urgent in substance but matching no hard floor: the engine is meant to learn this
            // from corrections, so it is measured rather than asserted.
            Sample("com.slack", "Production down", "The checkout service is returning 500s for all users", NotificationCategory.WORK, guaranteed = false, shouldRankHigh = true),
            Sample("com.slack", "Manager", "Can you jump on a call about the release right now?", NotificationCategory.WORK, guaranteed = false, shouldRankHigh = true, isConversation = true),
            Sample("com.phone", "Missed call", "You missed a call from Maya", NotificationCategory.OTHER, guaranteed = true, categoryHint = "call"),
            Sample("com.clock", "Alarm", "Alarm for 7:00 AM", NotificationCategory.OTHER, guaranteed = true, categoryHint = "alarm"),
            Sample("com.whatsapp", "Maya", "Are you free to talk in ten minutes?", NotificationCategory.SOCIAL, guaranteed = false, isConversation = true),
            Sample("com.whatsapp", "Dad", "Landed safely, will call later", NotificationCategory.SOCIAL, guaranteed = false, isConversation = true),
            Sample("com.shop.app", "Weekend sale", "Save up to 60% on everything this weekend only", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.shop.app", "Recommended for you", "New arrivals picked just for you", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.shop.app", "Secure your savings", "Lock in this discount before it expires", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.delivery", "Out for delivery", "Your package will arrive between 2 and 4pm", NotificationCategory.DELIVERY, guaranteed = false),
            Sample("com.food", "Driver nearby", "Your order is two minutes away", NotificationCategory.DELIVERY, guaranteed = false),
            Sample("com.android.systemui", "System update", "A software update is ready to install", NotificationCategory.SYSTEM, guaranteed = false),
            Sample("com.news.app", "Morning briefing", "Five stories to start your day", NotificationCategory.OTHER, guaranteed = false),
            Sample("com.social", "New follower", "Someone started following you", NotificationCategory.SOCIAL, guaranteed = false),
            Sample("com.game", "Daily reward", "Your daily bonus is waiting", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.calendar", "Meeting reminder", "Design review starts in 10 minutes", NotificationCategory.WORK, guaranteed = false),

            // --- second tranche -------------------------------------------------------------
            // Twenty samples cannot separate two encoders: one disagreement moves the score by
            // five points. These widen the set so a difference has to be real to show up, and
            // lean on the phrasings where a bag-of-tokens encoder is theoretically weakest —
            // negation, word order, and vocabulary shared across categories.
            Sample("com.bank.app", "Payment failed", "We could not process your card ending 4421", NotificationCategory.FINANCE, guaranteed = true),
            Sample("com.bank.app", "Transfer complete", "Your transfer of \$1,200 has been sent", NotificationCategory.FINANCE, guaranteed = true),
            Sample("com.auth.app", "New sign-in", "Someone signed in to your account from Berlin", NotificationCategory.SECURITY, guaranteed = true),
            Sample("com.auth.app", "Password changed", "Your password was changed successfully", NotificationCategory.SECURITY, guaranteed = true),
            Sample("com.email", "Invoice overdue", "Invoice 3312 is 14 days past due", NotificationCategory.FINANCE, guaranteed = true),
            Sample("com.slack", "Deploy failed", "The release pipeline stopped on the migration step", NotificationCategory.WORK, guaranteed = false, shouldRankHigh = true),
            Sample("com.slack", "Standup notes", "Notes from this morning are in the channel", NotificationCategory.WORK, guaranteed = false),
            Sample("com.email", "Contract signed", "The client countersigned this afternoon", NotificationCategory.WORK, guaranteed = false),
            Sample("com.calendar", "Tomorrow", "Retrospective moved to Thursday", NotificationCategory.WORK, guaranteed = false),
            Sample("com.whatsapp", "Sam", "Running fifteen minutes late, sorry", NotificationCategory.SOCIAL, guaranteed = false, isConversation = true),
            Sample("com.whatsapp", "Mum", "Give me a ring when you get a chance", NotificationCategory.SOCIAL, guaranteed = false, isConversation = true),
            Sample("com.messenger", "Group chat", "Anyone free for dinner on Friday?", NotificationCategory.SOCIAL, guaranteed = false, isConversation = true),
            Sample("com.social", "Photo tagged", "You were tagged in a photo", NotificationCategory.SOCIAL, guaranteed = false),
            Sample("com.shop.app", "Final hours", "Everything must go before midnight", NotificationCategory.PROMOTION, guaranteed = false),
            // Promotion borrowing security vocabulary on purpose.
            Sample("com.shop.app", "Your account needs attention", "Claim your reward points before they expire", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.travel", "Fare drop", "Flights to Lisbon are cheaper this week", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.delivery", "Delivered", "Your parcel was left in the porch", NotificationCategory.DELIVERY, guaranteed = false),
            Sample("com.delivery", "Delayed", "Your shipment will now arrive on Thursday", NotificationCategory.DELIVERY, guaranteed = false),
            Sample("com.food", "Order confirmed", "The restaurant is preparing your food", NotificationCategory.DELIVERY, guaranteed = false),
            Sample("com.android.systemui", "Storage low", "Less than 1 GB of space remains", NotificationCategory.SYSTEM, guaranteed = false),
            Sample("com.android.systemui", "Battery saver on", "Battery saver turned on at 15%", NotificationCategory.SYSTEM, guaranteed = false),
            Sample("com.android.vending", "Apps updated", "Three apps were updated overnight", NotificationCategory.SYSTEM, guaranteed = false),
            Sample("com.news.app", "Breaking", "Central bank holds interest rates", NotificationCategory.OTHER, guaranteed = false),
            Sample("com.podcast", "New episode", "The show you follow just published", NotificationCategory.OTHER, guaranteed = false),
            Sample("com.fitness", "Move goal", "You are 400 steps from your goal", NotificationCategory.OTHER, guaranteed = false),
            Sample("com.game", "Come back", "Your village misses you", NotificationCategory.PROMOTION, guaranteed = false),
            Sample("com.phone", "Voicemail", "New voicemail from an unknown number", NotificationCategory.OTHER, guaranteed = false),
            Sample("com.clock", "Timer finished", "Your 20 minute timer is done", NotificationCategory.OTHER, guaranteed = true, categoryHint = "alarm"),
            Sample("com.health", "Prescription ready", "Collect your prescription from the pharmacy", NotificationCategory.OTHER, guaranteed = false, shouldRankHigh = true),
            Sample("com.school", "Absence recorded", "Your child was marked absent this morning", NotificationCategory.OTHER, guaranteed = false, shouldRankHigh = true),
        )
    }
}
