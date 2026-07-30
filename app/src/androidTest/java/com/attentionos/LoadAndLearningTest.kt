package com.attentionos

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.attentionos.ai.StaticEmbeddingAnalyzer
import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionPolicy
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.NotificationSignal
import com.attentionos.domain.PriorityEngine
import com.attentionos.training.EmbeddingCodec
import com.attentionos.training.PersonalizedAttentionModel
import com.attentionos.training.TrainingSample
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flood the engine, then teach it, and check both halves actually hold up.
 *
 * Two separate claims are being tested. First, that the classifier keeps its accuracy and its
 * latency budget under a burst far larger than a real day — a notification storm is exactly when
 * an attention app must not fall over. Second, that corrections *change the model in the
 * direction taught*, which is the app's headline promise and had never been measured end to end;
 * every existing test covered a mechanism in isolation.
 *
 * Content is generated with a fixed seed so a failure is reproducible rather than a story about
 * a bad afternoon.
 */
class LoadAndLearningTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Generated(
        val signal: NotificationSignal,
        val expectSafetyFloor: Boolean,
    )

    /**
     * A day's notifications, shuffled: real traffic is not sorted by category, and processing
     * order must not matter.
     */
    private fun corpus(count: Int, seed: Int): List<Generated> {
        val random = Random(seed)
        val out = mutableListOf<Generated>()
        while (out.size < count) {
            val template = TEMPLATES.random(random)
            val index = out.size
            out += Generated(
                signal = NotificationSignal(
                    packageName = template.pkg,
                    title = template.title,
                    // Varied bodies so the embedding cache cannot answer everything and hide the
                    // real per-notification cost.
                    text = "${template.body} (#$index)",
                    postedAt = System.currentTimeMillis(),
                    isConversation = template.conversation,
                    isOngoing = false,
                    categoryHint = template.hint,
                ),
                expectSafetyFloor = template.safetyFloor,
            )
        }
        return out.shuffled(random)
    }

    @Test
    fun aFloodIsClassifiedWithoutLosingAccuracyOrTheLatencyBudget() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val notifications = corpus(FLOOD_SIZE, seed = 20260730)

        var floorsHeld = 0
        var floorsTotal = 0
        val failures = mutableListOf<String>()
        val latencies = mutableListOf<Long>()

        for (generated in notifications) {
            lateinit var decision: com.attentionos.domain.AttentionDecision
            latencies += measureNanoTime {
                decision = engine.decide(
                    signal = generated.signal,
                    context = AttentionContext(focusModeEnabled = false, hourOfDay = 14),
                    memory = null,
                )
            } / 1_000

            if (generated.expectSafetyFloor) {
                floorsTotal++
                if (decision.priority.ordinal <= AttentionPriority.HIGH.ordinal) {
                    floorsHeld++
                } else {
                    failures += "${generated.signal.title} -> ${decision.priority}"
                }
            }
        }

        val sorted = latencies.sorted()
        val median = sorted[sorted.size / 2] / 1000.0
        val p99 = sorted[(sorted.size * 99) / 100] / 1000.0
        val worst = sorted.last() / 1000.0
        Log.i(
            TAG,
            """
            |
            |=== flood ======================================================
            |  notifications      ${notifications.size}
            |  safety floors held $floorsHeld/$floorsTotal
            |  latency median     ${"%.2f".format(median)} ms
            |  latency p99        ${"%.2f".format(p99)} ms
            |  latency worst      ${"%.2f".format(worst)} ms
            |================================================================
            """.trimMargin(),
        )

        assertTrue("safety floors broke under load: $failures", failures.isEmpty())
        // A storm must not degrade the per-notification cost. Generous against the ~2ms median
        // so this fails on a regression rather than on emulator noise.
        assertTrue("p99 latency ${"%.2f".format(p99)}ms exceeds budget", p99 <= P99_BUDGET_MS)
    }

    @Test
    fun theSameNotificationAlwaysGetsTheSameDecision() {
        // Classification must be a pure function of its inputs. If it drifts, a user correcting
        // one notification would see unrelated ones change, and no amount of teaching would feel
        // like progress.
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val notifications = corpus(60, seed = 7)
        val attentionContext = AttentionContext(focusModeEnabled = false, hourOfDay = 9)

        val first = notifications.map { engine.decide(it.signal, attentionContext, null).priority }
        val second = notifications.map { engine.decide(it.signal, attentionContext, null).priority }
        assertEquals("repeated classification must be identical", first, second)
    }

    @Test
    fun attentionModeDecidesWhetherLowPriorityIsQueuedAndNothingElse() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val notifications = corpus(80, seed = 11)

        var queuedWithModeOn = 0
        var queuedWithModeOff = 0
        for (generated in notifications) {
            val on = engine.decide(
                generated.signal,
                AttentionContext(focusModeEnabled = true, hourOfDay = 14),
                null,
            )
            val off = engine.decide(
                generated.signal,
                AttentionContext(focusModeEnabled = false, hourOfDay = 14),
                null,
            )
            if (on.shouldQueue) queuedWithModeOn++
            if (off.shouldQueue) queuedWithModeOff++

            // Attention Mode deliberately subtracts from the score of anything that is neither
            // urgent nor protected — that penalty is *how* the queue gets anything to hold. So a
            // priority may drop. What must never happen is the reverse: the mode is a calming
            // control, and raising a priority because the user asked for quiet would be absurd.
            assertTrue(
                "Attention Mode raised '${generated.signal.title}' from " +
                    "${off.priority} to ${on.priority}",
                on.priority.ordinal >= off.priority.ordinal,
            )
            if (generated.expectSafetyFloor) {
                assertEquals(
                    "Attention Mode downgraded a protected alert: ${generated.signal.title}",
                    off.priority,
                    on.priority,
                )
                assertTrue(
                    "a protected alert was queued: ${generated.signal.title}",
                    !on.shouldQueue,
                )
            }
        }

        Log.i(TAG, "queued with mode on: $queuedWithModeOn, off: $queuedWithModeOff")
        assertEquals("nothing may be queued with Attention Mode off", 0, queuedWithModeOff)
        assertTrue("Attention Mode should queue something in this corpus", queuedWithModeOn > 0)
    }

    @Test
    fun trainingMovesThePredictionInTheDirectionTaught() {
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)

        // A consistent user: work notifications matter, promotions do not. This is the claim the
        // whole product rests on, so it is measured on held-out items the model never saw.
        // Drawn from a wide corpus because only a few templates carry each package; 300 draws
        // yields enough of both classes for a fit to mean anything.
        val trainWork = corpus(300, seed = 101).filter { it.signal.packageName == WORK_PKG }
        val trainPromo = corpus(300, seed = 202).filter { it.signal.packageName == PROMO_PKG }
        val heldOutWork = corpus(300, seed = 303).filter { it.signal.packageName == WORK_PKG }

        val samples = mutableListOf<TrainingSample>()
        fun teach(items: List<Generated>, important: Boolean) {
            for (item in items) {
                val decision = engine.decide(
                    item.signal,
                    AttentionContext(focusModeEnabled = false, hourOfDay = 10),
                    null,
                )
                val embedding = decision.semanticEmbedding ?: continue
                val features = PersonalizedAttentionModel.features(
                    embedding = embedding,
                    hourOfDay = 10,
                    senderImportance = 0.5f,
                    senderOpenRate = 0.5f,
                    focusModeEnabled = false,
                    baseScore = decision.score,
                    packageName = item.signal.packageName,
                ) ?: continue
                samples += TrainingSample(features, important)
            }
        }
        teach(trainWork, important = true)
        teach(trainPromo, important = false)
        assertTrue("need both classes to learn anything", samples.size >= 20)

        val untrained = PersonalizedAttentionModel.fresh()
        val trained = PersonalizedAttentionModel.refit(samples)

        fun scoreHeldOut(state: com.attentionos.training.PersonalizedModelState): Float {
            val scores = heldOutWork.mapNotNull { item ->
                val decision = engine.decide(
                    item.signal,
                    AttentionContext(focusModeEnabled = false, hourOfDay = 10),
                    null,
                )
                val embedding = decision.semanticEmbedding ?: return@mapNotNull null
                val features = PersonalizedAttentionModel.features(
                    embedding = embedding,
                    hourOfDay = 10,
                    senderImportance = 0.5f,
                    senderOpenRate = 0.5f,
                    focusModeEnabled = false,
                    baseScore = decision.score,
                    packageName = item.signal.packageName,
                ) ?: return@mapNotNull null
                PersonalizedAttentionModel.predict(state, features)
            }
            return scores.average().toFloat()
        }

        val before = scoreHeldOut(untrained)
        val after = scoreHeldOut(trained)
        Log.i(
            TAG,
            """
            |
            |=== learning ===================================================
            |  taught             ${samples.size} corrections
            |  held-out work items ${heldOutWork.size}
            |  P(important) before ${"%.3f".format(before)}
            |  P(important) after  ${"%.3f".format(after)}
            |  calibration         slope ${"%.3f".format(trained.calibrationSlope)}, """.trimMargin() +
                "intercept ${"%.3f".format(trained.calibrationIntercept)}\n" +
                "================================================================",
        )

        // An untrained model sits at 0.5 by construction; a trained one must have moved up on
        // notifications like the ones it was taught to value.
        assertEquals("an untrained model should be undecided", 0.5f, before, 0.02f)
        assertTrue(
            "training did not raise P(important) on held-out work items: $before -> $after",
            after > before + MINIMUM_LEARNING_SHIFT,
        )
    }

    @Test
    fun trainingCannotSilenceAProtectedAlert() {
        // The adversarial case: a user who marks *everything* unimportant, including security.
        // The safety floors must survive that, because they are the one promise personalization
        // is never allowed to override.
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)
        val everything = corpus(60, seed = 404)

        val samples = everything.mapNotNull { item ->
            val decision = engine.decide(
                item.signal,
                AttentionContext(focusModeEnabled = false, hourOfDay = 3),
                null,
            )
            val embedding = decision.semanticEmbedding ?: return@mapNotNull null
            PersonalizedAttentionModel.features(
                embedding = embedding,
                hourOfDay = 3,
                senderImportance = 0.02f,
                senderOpenRate = 0.02f,
                focusModeEnabled = false,
                baseScore = decision.score,
                packageName = item.signal.packageName,
            )?.let { TrainingSample(it, important = false) }
        }
        // A single-class set has no boundary; add a token positive so the fit is well-formed and
        // the model is as hostile as it can legitimately be.
        val hostile = PersonalizedAttentionModel.refit(
            samples + samples.first().copy(important = true),
        )

        for (item in everything.filter { it.expectSafetyFloor }) {
            val base = engine.decide(
                item.signal,
                AttentionContext(focusModeEnabled = false, hourOfDay = 3),
                null,
            )
            val safetyProtected = AttentionPolicy.isSafetyProtected(
                category = base.category,
                semanticUrgency = base.semanticUrgency,
                categoryHint = item.signal.categoryHint,
            )
            assertTrue(
                "'${item.signal.title}' lost its safety protection",
                safetyProtected,
            )
            val adjusted = com.attentionos.training.PersonalizedDecisionPolicy.apply(
                base = base,
                probabilityImportant = 0f,
                context = AttentionContext(focusModeEnabled = false, hourOfDay = 3),
                safetyProtected = true,
            )
            assertTrue(
                "a protected alert was downgraded to ${adjusted.priority}",
                adjusted.priority.ordinal <= AttentionPriority.HIGH.ordinal,
            )
            assertTrue("a protected alert was queued", !adjusted.shouldQueue)
        }
    }

    @Test
    fun embeddingsRoundTripThroughStorageWithoutBreakingTheModel() {
        // Every correction is replayed from a quantised embedding read back out of the database.
        // If the round trip drifted, learning would degrade invisibly over time.
        val analyzer = StaticEmbeddingAnalyzer(context)
        analyzer.warmUp()
        val engine = PriorityEngine(analyzer)

        var worst = 0f
        for (item in corpus(40, seed = 505)) {
            val embedding = engine.decide(
                item.signal,
                AttentionContext(focusModeEnabled = false, hourOfDay = 12),
                null,
            ).semanticEmbedding ?: continue
            val restored = EmbeddingCodec.decode(EmbeddingCodec.encode(embedding))
            assertEquals(EmbeddingCodec.EXPECTED_DIMENSIONS, restored?.size)
            for (index in embedding.indices) {
                worst = maxOf(worst, abs(embedding[index] - restored!![index]))
            }
        }
        Log.i(TAG, "worst per-dimension quantisation error: ${"%.5f".format(worst)}")
        assertTrue("quantisation error $worst is too large to train on", worst < 0.02f)
    }

    private data class Template(
        val pkg: String,
        val title: String,
        val body: String,
        val safetyFloor: Boolean = false,
        val conversation: Boolean = false,
        val hint: String? = null,
    )

    private companion object {
        const val TAG = "AttentionLoad"
        const val FLOOD_SIZE = 400
        const val P99_BUDGET_MS = 25.0
        const val MINIMUM_LEARNING_SHIFT = 0.10f
        const val WORK_PKG = "com.slack"
        const val PROMO_PKG = "com.shop.app"

        val TEMPLATES = listOf(
            Template("com.bank.app", "Security alert", "Unusual sign-in from a new device", safetyFloor = true),
            Template("com.auth.app", "Your code", "Verification code expires in five minutes", safetyFloor = true),
            Template("com.bank.app", "Card charged", "A payment was debited from your account", safetyFloor = true),
            Template("com.bank.app", "Payment failed", "We could not process your card", safetyFloor = true),
            Template("com.phone", "Missed call", "You missed a call", safetyFloor = true, hint = "call"),
            Template("com.clock", "Alarm", "Your alarm is ringing", safetyFloor = true, hint = "alarm"),
            Template(WORK_PKG, "Production down", "The checkout service is returning errors for all users"),
            Template(WORK_PKG, "Manager", "Can you jump on a call about the release", conversation = true),
            Template(WORK_PKG, "Deploy failed", "The release pipeline stopped on the migration step"),
            Template(WORK_PKG, "Standup notes", "Notes from this morning are in the channel"),
            Template("com.whatsapp", "Maya", "Are you free to talk in ten minutes", conversation = true),
            Template("com.whatsapp", "Dad", "Landed safely will call later", conversation = true),
            Template(PROMO_PKG, "Weekend sale", "Save up to sixty percent on everything"),
            Template(PROMO_PKG, "Recommended for you", "New arrivals picked just for you"),
            Template(PROMO_PKG, "Final hours", "Everything must go before midnight"),
            Template("com.delivery", "Out for delivery", "Your package will arrive this afternoon"),
            Template("com.food", "Driver nearby", "Your order is two minutes away"),
            Template("com.android.systemui", "System update", "A software update is ready to install"),
            Template("com.android.systemui", "Storage low", "Less than one gigabyte of space remains"),
            Template("com.news.app", "Morning briefing", "Five stories to start your day"),
            Template("com.social", "New follower", "Someone started following you"),
            Template("com.game", "Daily reward", "Your daily bonus is waiting"),
        )
    }
}
