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

    private fun corpus(): List<Case> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open(CORPUS_ASSET)
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
    }

    private companion object {
        const val TAG = "AttentionCorpus"
        const val CORPUS_ASSET = "notification-corpus.json"

        /** Measured 2026-07-30. Raise when the architecture improves; never relax. */
        const val RECALL_BASELINE = 55
    }
}
