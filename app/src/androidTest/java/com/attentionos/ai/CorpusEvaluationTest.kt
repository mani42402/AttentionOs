package com.attentionos.ai

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.NotificationSignal
import com.attentionos.domain.PriorityEngine
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full corpus: 15 languages x 15 message kinds, read from
 * `androidTest/assets/notification-corpus.json`.
 *
 * Includes **Roman Urdu and Hinglish** — Latin script carrying Urdu or Hindi words, which is how
 * a large share of South Asia actually types. Those were missing from every earlier evaluation,
 * and they are the hardest case for this architecture: the tokens exist in the vocabulary
 * ("ap", "ho", "kha", "ao" are all present) so nothing looks broken, but their embeddings were
 * learned from whatever those letter sequences mean across every other language. Coverage is not
 * comprehension.
 *
 * The per-kind breakdown is the useful output. It shows *which* kinds of message the engine can
 * and cannot rank, which a single accuracy figure hides.
 */
class CorpusEvaluationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Case(
        val lang: String,
        val kind: String,
        val pkg: String,
        val title: String,
        val body: String,
        val mustReach: Boolean,
        val conversation: Boolean,
        val hint: String?,
    )

    private fun corpus(asset: String = CORPUS_ASSET): List<Case> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open(asset)
            .bufferedReader()
            .use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            Case(
                lang = o.getString("lang"),
                kind = o.getString("kind"),
                pkg = o.getString("pkg"),
                title = o.getString("title"),
                body = o.getString("body"),
                mustReach = o.getBoolean("mustReach"),
                conversation = o.getBoolean("conversation"),
                hint = if (o.isNull("hint")) null else o.getString("hint"),
            )
        }
    }

    /**
     * The corpus the descriptions were written against.
     *
     * Useful as a regression guard and nothing more: the same person wrote both, so a good score
     * here is partly a measure of self-consistency. [scoreHeldOutCorpus] is the honest one.
     */
    @Test
    fun scoreTheWholeCorpus() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val vocabulary = context.assets
            .open(StaticEmbeddingAnalyzer.VOCAB_ASSET)
            .bufferedReader()
            .use { it.readLines() }
        val tokenizer = WordPieceTokenizer(vocabulary)
        val unknownId = vocabulary.indexOf("[UNK]")

        val cases = corpus()
        assertTrue("corpus did not load", cases.size >= 200)

        data class Tally(
            var reached: Int = 0,
            var must: Int = 0,
            var quietRight: Int = 0,
            var quiet: Int = 0,
            var unknown: Int = 0,
            var tokens: Int = 0,
        )

        val byLang = linkedMapOf<String, Tally>()
        val byKind = linkedMapOf<String, Tally>()

        for (case in cases) {
            val ids = tokenizer.encodePieces(
                "${case.title} ${case.body}",
                StaticEmbeddingAnalyzer.MAX_TOKENS,
            )
            val decision = engine.decide(
                signal = NotificationSignal(
                    packageName = case.pkg,
                    title = case.title,
                    text = case.body,
                    postedAt = System.currentTimeMillis(),
                    isConversation = case.conversation,
                    isOngoing = false,
                    categoryHint = case.hint,
                ),
                context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
                memory = null,
            )
            val prominent = decision.priority.ordinal <= AttentionPriority.HIGH.ordinal

            for (tally in listOf(
                byLang.getOrPut(case.lang) { Tally() },
                byKind.getOrPut(case.kind) { Tally() },
            )) {
                tally.tokens += ids.size
                tally.unknown += ids.count { it == unknownId }
                if (case.mustReach) {
                    tally.must++
                    if (prominent) tally.reached++
                } else {
                    tally.quiet++
                    if (!prominent) tally.quietRight++
                }
            }
        }

        fun render(title: String, rows: Map<String, Tally>) = buildString {
            append("\n=== $title ".padEnd(64, '=')).append('\n')
            rows.entries
                .sortedBy { if (it.value.must == 0) 0.0 else -it.value.reached.toDouble() / it.value.must }
                .forEach { (key, t) ->
                    val recall = if (t.must == 0) 0.0 else t.reached * 100.0 / t.must
                    val quiet = if (t.quiet == 0) 0.0 else t.quietRight * 100.0 / t.quiet
                    append(
                        "  %-14s unknown %5.1f%%   must-reach %2d/%-2d (%3.0f%%)   quiet %2d/%-2d (%3.0f%%)\n"
                            .format(key, t.unknown * 100.0 / t.tokens, t.reached, t.must, recall, t.quietRight, t.quiet, quiet),
                    )
                }
            append("=".repeat(64))
        }

        val totalMust = byLang.values.sumOf { it.must }
        val totalReached = byLang.values.sumOf { it.reached }
        val totalQuiet = byLang.values.sumOf { it.quiet }
        val totalQuietRight = byLang.values.sumOf { it.quietRight }

        Log.i(TAG, render("by language", byLang))
        Log.i(TAG, render("by message kind", byKind))
        Log.i(
            TAG,
            "\nTOTAL must-reach $totalReached/$totalMust " +
                "(${"%.0f".format(totalReached * 100.0 / totalMust)}%)   " +
                "quiet-right $totalQuietRight/$totalQuiet " +
                "(${"%.0f".format(totalQuietRight * 100.0 / totalQuiet)}%)",
        )

        // Baseline only, so the figure cannot silently regress while the architecture is reworked.
        assertTrue(
            "corpus must-reach recall fell below the recorded baseline: $totalReached/$totalMust",
            totalReached >= RECALL_BASELINE,
        )
        // Recall is worthless if everything is promoted. Pinned together so a change has to keep
        // both — raising recall by shouting at the user is not an improvement.
        assertTrue(
            "noise rejection fell below the recorded baseline: $totalQuietRight/$totalQuiet",
            totalQuietRight >= QUIET_BASELINE,
        )
    }

    /**
     * The honest test: 135 notifications whose scenarios the descriptions never mention.
     *
     * Written under three rules. No scenario named in `AttentionDescriptions` — no hospital, rent,
     * school or OTP wording reused. Informal register throughout: "u free tn?", "where r u?? been
     * waiting 20 mins", missing vowels, ALL CAPS, emoji. And real notification formats — "+18 new
     * messages", "Now playing", "Screenshot saved" — rather than tidy sentences.
     *
     * 35 scenario kinds none of the descriptions name: gas leak, fire alarm, a death in the
     * family, an urgent blood request, exam results, a job interview, a visa appointment, a pet
     * out of surgery, a towed car, a power cut, a delayed salary, an approved loan, an insurance
     * claim, a 2FA push, a ride that has arrived, a meeting starting now, a shared document, a
     * code review, a monitoring page, a crypto move, a cricket score, now playing, a screenshot,
     * a low battery, a birthday, traffic, and a building notice — across 17 languages.
     *
     * The point is to find out whether the descriptions generalise or whether they only match the
     * hand that wrote them. Nothing here was used to tune a description, and the recorded baseline
     * is whatever the first run produced.
     */
    @Test
    fun scoreHeldOutCorpus() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val cases = corpus(HELD_OUT_ASSET)
        assertTrue("held-out corpus did not load", cases.size >= 120)

        var reached = 0
        var must = 0
        var quietRight = 0
        var quiet = 0
        val missed = mutableListOf<String>()
        val overAlerted = mutableListOf<String>()
        val byLang = linkedMapOf<String, IntArray>()

        for (case in cases) {
            val decision = engine.decide(
                signal = NotificationSignal(
                    packageName = case.pkg,
                    title = case.title,
                    text = case.body,
                    postedAt = System.currentTimeMillis(),
                    isConversation = case.conversation,
                    isOngoing = false,
                    categoryHint = case.hint,
                ),
                context = AttentionContext(focusModeEnabled = true, hourOfDay = 14),
                memory = null,
            )
            val prominent = decision.priority.ordinal <= AttentionPriority.HIGH.ordinal
            // [reached, must, quietRight, quiet]
            val row = byLang.getOrPut(case.lang) { IntArray(4) }
            if (case.mustReach) {
                must++; row[1]++
                if (prominent) { reached++; row[0]++ } else {
                    missed += "${case.lang}/${case.kind}: \"${case.body.take(38)}\" -> ${decision.priority}"
                }
            } else {
                quiet++; row[3]++
                if (!prominent) { quietRight++; row[2]++ } else {
                    overAlerted += "${case.lang}/${case.kind}: \"${case.body.take(38)}\" -> ${decision.priority}"
                }
            }
        }

        val report = StringBuilder("\n=== HELD OUT: scenarios the descriptions never mention ").append("\n")
        byLang.entries
            .sortedBy { -(if (it.value[1] == 0) 0.0 else it.value[0].toDouble() / it.value[1]) }
            .forEach { (lang, r) ->
                report.append(
                    "  %-12s must-reach %2d/%-2d (%3.0f%%)   quiet %2d/%-2d\n".format(
                        lang, r[0], r[1],
                        if (r[1] == 0) 0.0 else r[0] * 100.0 / r[1], r[2], r[3],
                    ),
                )
            }
        report.append(
            "\n  TOTAL must-reach $reached/$must (${"%.0f".format(reached * 100.0 / must)}%)" +
                "   quiet-right $quietRight/$quiet (${"%.0f".format(quietRight * 100.0 / quiet)}%)",
        )
        Log.i(TAG, report.toString())
        Log.i(TAG, "\nMISSED (would have mattered):\n  " + missed.joinToString("\n  "))
        Log.i(TAG, "\nOVER-ALERTED (noise promoted):\n  " + overAlerted.joinToString("\n  "))

        assertTrue(
            "held-out recall fell below the recorded baseline: $reached/$must",
            reached >= HELD_OUT_RECALL_BASELINE,
        )
        assertTrue(
            "held-out noise rejection fell below the recorded baseline: $quietRight/$quiet",
            quietRight >= HELD_OUT_QUIET_BASELINE,
        )
    }

    private companion object {
        const val TAG = "AttentionCorpus"
        const val HELD_OUT_ASSET = "holdout-corpus.json"

        /**
         * First-run figures, never tuned against: 41/85 recall, 34/50 noise rejection.
         *
         * The comparison that matters, measured by reverting the decision logic and re-running
         * this same file:
         *
         * ```
         *                        own corpus        held out
         *   weighted sum        71/120  59%      34/85  40%   quiet 40/50  80%
         *   descriptions       110/120  92%      41/85  48%   quiet 34/50  68%
         * ```
         *
         * The 92% was largely self-consistency — the same hand wrote the descriptions and that
         * corpus. On scenarios nobody wrote a description for, the gain is 8 points of recall
         * bought with 12 points of noise. That is close to a wash, and it falsifies the claim
         * that ~50 sentences bound the space: this set alone needed a death in the family, an
         * urgent blood request, a visa appointment, a meeting starting now, a ride that has
         * arrived, and an entire informal register ("where r u?? been waiting 20 mins") that
         * none of them cover.
         */
        const val HELD_OUT_RECALL_BASELINE = 41
        const val HELD_OUT_QUIET_BASELINE = 34
        const val CORPUS_ASSET = "notification-corpus.json"

        /**
         * Measured 2026-07-30 after the descriptions replaced the weighted sum: 110/120.
         * Was 71/120 when nine tuned constants and four thresholds decided.
         *
         * Raise when the architecture improves; never relax to make a change pass.
         */
        const val RECALL_BASELINE = 108

        /** 85/105. Was 86/105 before, so recall rose 33 points without paying in noise. */
        const val QUIET_BASELINE = 83
    }
}
