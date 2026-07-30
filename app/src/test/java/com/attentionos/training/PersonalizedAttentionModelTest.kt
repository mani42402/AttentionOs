package com.attentionos.training

import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionDecision
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.NotificationCategory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PACKAGE_BUCKETS = PersonalizedAttentionModel.PACKAGE_BUCKETS

class PersonalizedAttentionModelTest {
    private val embedding = FloatArray(EmbeddingCodec.EXPECTED_DIMENSIONS) { index ->
        if (index % 2 == 0) 0.04f else -0.04f
    }
    private val features = requireNotNull(
        PersonalizedAttentionModel.features(
            embedding = embedding,
            hourOfDay = 9,
            senderImportance = 0.7f,
            senderOpenRate = 0.8f,
            focusModeEnabled = false,
            baseScore = 0.55f,
        ),
    )

    @Test
    fun `explicit corrections move probability in the expected direction`() {
        var positive: PersonalizedModelState? = null
        var negative: PersonalizedModelState? = null
        repeat(20) {
            positive = PersonalizedAttentionModel.update(positive, features, important = true)
            negative = PersonalizedAttentionModel.update(negative, features, important = false)
        }

        val positiveProbability = PersonalizedAttentionModel.predict(positive!!, features)
        val negativeProbability = PersonalizedAttentionModel.predict(negative!!, features)
        assertTrue(positiveProbability > 0.70f)
        assertTrue(negativeProbability < 0.30f)
        assertTrue(positiveProbability - negativeProbability > 0.45f)
    }

    @Test
    fun `model activates only with enough examples from both choices`() {
        val allPositive = PersonalizedModelState(
            weights = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT),
            bias = 0f,
            positiveCount = 50,
            negativeCount = 0,
        )
        val balancedEnough = allPositive.copy(
            positiveCount = 40,
            negativeCount = 10,
            evaluationCount = 45,
            personalCorrectCount = 36,
            baselineCorrectCount = 34,
            importantEvaluationCount = 20,
            importantCorrectCount = 18,
            notImportantEvaluationCount = 25,
            falseImportantCount = 4,
        )

        assertFalse(allPositive.isActive)
        assertTrue(balancedEnough.isActive)
    }

    @Test
    fun `shadow evaluation is recorded before the training update`() {
        val updated = PersonalizedAttentionModel.update(
            current = null,
            features = features,
            important = false,
            predictionBeforeUpdate = 0.8f,
            baselineWasImportant = false,
        )

        assertEquals(1, updated.evaluationCount)
        assertEquals(0, updated.personalCorrectCount)
        assertEquals(1, updated.baselineCorrectCount)
        assertEquals(1, updated.notImportantEvaluationCount)
        assertEquals(1, updated.falseImportantCount)
    }

    @Test
    fun `model stays in shadow mode when it underperforms the baseline`() {
        val state = PersonalizedModelState(
            weights = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT),
            bias = 0f,
            positiveCount = 30,
            negativeCount = 30,
            evaluationCount = 50,
            personalCorrectCount = 35,
            baselineCorrectCount = 42,
            importantEvaluationCount = 20,
            importantCorrectCount = 18,
            notImportantEvaluationCount = 30,
            falseImportantCount = 8,
        )

        assertFalse(state.isActive)
    }

    @Test
    fun `float weights persist without precision loss`() {
        val weights = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT) { it / 997f }
        val restored = ModelWeightsCodec.decode(ModelWeightsCodec.encode(weights))

        assertArrayEquals(weights, restored, 0f)
    }

    @Test
    fun `protected alert cannot be downgraded by personal model`() {
        val base = decision(score = 0.72f, priority = AttentionPriority.HIGH)
        val adjusted = PersonalizedDecisionPolicy.apply(
            base = base,
            probabilityImportant = 0f,
            context = AttentionContext(focusModeEnabled = true, hourOfDay = 2),
            safetyProtected = true,
        )

        assertEquals(base.score, adjusted.score)
        assertEquals(base.priority, adjusted.priority)
    }

    @Test
    fun `unprotected alert can be adjusted conservatively`() {
        val base = decision(score = 0.50f, priority = AttentionPriority.MEDIUM)
        val adjusted = PersonalizedDecisionPolicy.apply(
            base = base,
            probabilityImportant = 1f,
            context = AttentionContext(focusModeEnabled = false, hourOfDay = 12),
            safetyProtected = false,
        )

        assertTrue(adjusted.score > base.score)
        assertTrue(adjusted.score < 0.68f)
        assertEquals(AttentionPriority.MEDIUM, adjusted.priority)
    }

    private fun decision(score: Float, priority: AttentionPriority) = AttentionDecision(
        priority = priority,
        category = NotificationCategory.OTHER,
        score = score,
        explanation = "base",
        shouldQueue = false,
    )
    @Test
    fun `package bucket is stable and inside the table`() {
        val slack = PersonalizedAttentionModel.bucketOf("com.slack")
        assertEquals(slack, PersonalizedAttentionModel.bucketOf("com.slack"))
        for (name in listOf("com.slack", "com.whatsapp", "com.bank.app", "", "a")) {
            val bucket = PersonalizedAttentionModel.bucketOf(name)
            assertTrue("bucket $bucket out of range for $name", bucket in 0 until PACKAGE_BUCKETS)
        }
    }

    @Test
    fun `package feature sets exactly one bucket and leaves the rest clear`() {
        val withPackage = requireNotNull(
            PersonalizedAttentionModel.features(
                embedding = embedding,
                hourOfDay = 9,
                senderImportance = 0.7f,
                senderOpenRate = 0.8f,
                focusModeEnabled = false,
                baseScore = 0.55f,
                packageName = "com.slack",
            ),
        )
        val start = EmbeddingCodec.EXPECTED_DIMENSIONS + PersonalizedAttentionModel.CONTEXT_FEATURES
        val set = (start until start + PACKAGE_BUCKETS).filter { withPackage[it] != 0f }
        assertEquals(listOf(start + PersonalizedAttentionModel.bucketOf("com.slack")), set)
    }

    @Test
    fun `an unknown package leaves every bucket clear`() {
        val start = EmbeddingCodec.EXPECTED_DIMENSIONS + PersonalizedAttentionModel.CONTEXT_FEATURES
        assertTrue(
            "no package should mean no bucket",
            (start until start + PACKAGE_BUCKETS).all { features[it] == 0f },
        )
    }

    @Test
    fun `calibration keeps the ranking the weights learned`() {
        // Separable set: the raw fit becomes over-confident, which is what Platt scaling is for.
        val samples = (0 until 80).map { index ->
            val important = index % 2 == 0
            val vector = FloatArray(EmbeddingCodec.EXPECTED_DIMENSIONS) {
                if (important) 0.05f else -0.05f
            }
            TrainingSample(
                features = requireNotNull(
                    PersonalizedAttentionModel.features(
                        embedding = vector,
                        hourOfDay = if (important) 10 else 22,
                        senderImportance = if (important) 0.9f else 0.1f,
                        senderOpenRate = if (important) 0.9f else 0.1f,
                        focusModeEnabled = false,
                        baseScore = if (important) 0.8f else 0.2f,
                        packageName = if (important) "com.work" else "com.shop",
                    ),
                ),
                important = important,
            )
        }
        val model = PersonalizedAttentionModel.refit(samples)

        assertTrue("slope must stay positive or the ranking inverts", model.calibrationSlope > 0f)
        val importantScore = PersonalizedAttentionModel.predict(model, samples.first { it.important }.features)
        val quietScore = PersonalizedAttentionModel.predict(model, samples.first { !it.important }.features)
        assertTrue(
            "important ($importantScore) must still outrank quiet ($quietScore)",
            importantScore > quietScore,
        )
    }

    @Test
    fun `calibration stays identity until there is enough data to fit it`() {
        val samples = (0 until 8).map { index ->
            val important = index % 2 == 0
            TrainingSample(
                features = requireNotNull(
                    PersonalizedAttentionModel.features(
                        embedding = embedding,
                        hourOfDay = 9,
                        senderImportance = if (important) 0.9f else 0.1f,
                        senderOpenRate = 0.5f,
                        focusModeEnabled = false,
                        baseScore = 0.5f,
                    ),
                ),
                important = important,
            )
        }
        val model = PersonalizedAttentionModel.refit(samples)
        assertEquals(1f, model.calibrationSlope, 0f)
        assertEquals(0f, model.calibrationIntercept, 0f)
    }

}
