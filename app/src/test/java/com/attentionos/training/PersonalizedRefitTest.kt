package com.attentionos.training

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the replay-buffer refit and the centroid cold-start.
 *
 * The point of refitting is that one gradient step per correction underfits and depends on
 * arrival order; these tests pin both properties rather than trusting the reasoning.
 */
class PersonalizedRefitTest {

    private val dimensions = EmbeddingCodec.EXPECTED_DIMENSIONS

    /** A separable set: important examples lean +1 on the first axis, unimportant -1. */
    private fun separableSamples(count: Int, seed: Int = 7): List<TrainingSample> {
        val random = Random(seed)
        return (0 until count).map { index ->
            val important = index % 2 == 0
            val features = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT) {
                random.nextFloat() * 0.02f - 0.01f
            }
            features[0] = if (important) 1f else -1f
            TrainingSample(features, important)
        }
    }

    @Test
    fun `refit separates a linearly separable set`() {
        val samples = separableSamples(40)
        val model = PersonalizedAttentionModel.refit(samples)

        val correct = samples.count { sample ->
            (PersonalizedAttentionModel.predict(model, sample.features) >= 0.5f) == sample.important
        }
        assertEquals("refit should fit a separable set exactly", samples.size, correct)
    }

    @Test
    fun `refit beats a single online pass on the same data`() {
        // The concrete reason for the change: one step per example, seen once, underfits.
        val samples = separableSamples(40)

        var online: PersonalizedModelState? = null
        for (sample in samples) {
            online = PersonalizedAttentionModel.update(online, sample.features, sample.important)
        }
        val onlineCorrect = samples.count {
            (PersonalizedAttentionModel.predict(online!!, it.features) >= 0.5f) == it.important
        }

        val refitted = PersonalizedAttentionModel.refit(samples)
        val refitCorrect = samples.count {
            (PersonalizedAttentionModel.predict(refitted, it.features) >= 0.5f) == it.important
        }

        assertTrue(
            "refit ($refitCorrect) should be at least as accurate as one online pass ($onlineCorrect)",
            refitCorrect >= onlineCorrect,
        )
    }

    @Test
    fun `refit does not depend on the order corrections arrived`() {
        val samples = separableSamples(30)
        val forward = PersonalizedAttentionModel.refit(samples)
        val reversed = PersonalizedAttentionModel.refit(samples.reversed())

        val maximumDrift = forward.weights.indices.maxOf {
            abs(forward.weights[it] - reversed.weights[it])
        }
        assertTrue("weights drifted by $maximumDrift with order alone", maximumDrift < 0.05f)
    }

    @Test
    fun `refit is deterministic for the same input`() {
        val samples = separableSamples(20)
        val first = PersonalizedAttentionModel.refit(samples)
        val second = PersonalizedAttentionModel.refit(samples)
        assertTrue(first.weights.contentEquals(second.weights))
    }

    @Test
    fun `refit tolerates heavy class imbalance`() {
        // Inverse-frequency weighting is the imbalance fix; without it the rare class is
        // swamped and the model predicts the majority for everything.
        val random = Random(3)
        val samples = (0 until 60).map { index ->
            val important = index < 6
            val features = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT) {
                random.nextFloat() * 0.02f - 0.01f
            }
            features[0] = if (important) 1f else -1f
            TrainingSample(features, important)
        }
        val model = PersonalizedAttentionModel.refit(samples)
        val rareRecalled = samples.filter { it.important }.count {
            PersonalizedAttentionModel.predict(model, it.features) >= 0.5f
        }
        assertEquals("the minority class must not be swamped", 6, rareRecalled)
    }

    @Test
    fun `a single-class set produces no decision boundary`() {
        val samples = separableSamples(10).filter { it.important }
        val model = PersonalizedAttentionModel.refit(samples)
        assertTrue("weights should stay zero", model.weights.all { it == 0f })
        assertEquals(samples.size, model.positiveCount)
        assertEquals(0, model.negativeCount)
    }

    @Test
    fun `centroids need a few examples in both classes`() {
        assertEquals(null, PersonalizedAttentionModel.centroids(emptyList()))
        val onlyImportant = separableSamples(10).filter { it.important }
        assertEquals(null, PersonalizedAttentionModel.centroids(onlyImportant))
        assertTrue(PersonalizedAttentionModel.centroids(separableSamples(8)) != null)
    }

    @Test
    fun `prototype score separates the classes from very few examples`() {
        // Six corrections: far below the 50 the classifier needs before it may act.
        val samples = separableSamples(6)
        val centroids = PersonalizedAttentionModel.centroids(samples)!!

        val importantLike = FloatArray(dimensions).also { it[0] = 1f }
        val unimportantLike = FloatArray(dimensions).also { it[0] = -1f }

        assertTrue(
            PersonalizedAttentionModel.prototypeScore(importantLike, centroids) > 0.5f,
        )
        assertTrue(
            PersonalizedAttentionModel.prototypeScore(unimportantLike, centroids) < 0.5f,
        )
    }

    @Test
    fun `prototype evidence arrives long before full activation`() {
        val early = PersonalizedAttentionModel.fresh().copy(positiveCount = 3, negativeCount = 3)
        assertTrue("centroids should be usable early", early.hasPrototypeEvidence)
        assertFalse("but the classifier must still be gated", early.isActive)
    }

    @Test
    fun `centroid vectors survive a storage round trip`() {
        val centroids = PersonalizedAttentionModel.centroids(separableSamples(8))!!
        val restored = ModelWeightsCodec.decodeVector(
            ModelWeightsCodec.encodeVector(centroids.important),
        )
        assertTrue(centroids.important.contentEquals(restored))
    }

    @Test
    fun `malformed centroid bytes decode to null rather than garbage`() {
        assertEquals(null, ModelWeightsCodec.decodeVector(null))
        assertEquals(null, ModelWeightsCodec.decodeVector(ByteArray(0)))
        assertEquals(null, ModelWeightsCodec.decodeVector(ByteArray(7)))
    }
}
