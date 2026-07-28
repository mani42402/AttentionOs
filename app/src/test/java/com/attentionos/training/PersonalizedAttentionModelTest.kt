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
}
