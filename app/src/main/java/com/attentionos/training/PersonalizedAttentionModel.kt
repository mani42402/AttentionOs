package com.attentionos.training

import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionDecision
import com.attentionos.domain.AttentionPolicy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tiny online logistic classifier over frozen MiniLM embeddings and bounded context features.
 *
 * MiniLM remains frozen. Only these local weights are updated, once per explicit correction.
 * The model is intentionally inactive until it has enough examples from both classes.
 */
object PersonalizedAttentionModel {
    const val FEATURE_COUNT = EmbeddingCodec.EXPECTED_DIMENSIONS + 6
    const val MIN_EXAMPLES = 50
    const val MIN_PER_CLASS = 10
    const val MIN_EVALUATIONS = 40
    const val MIN_IMPORTANT_EVALUATIONS = 8
    const val MIN_ACCURACY = 0.65f
    const val MIN_IMPORTANT_RECALL = 0.80f
    const val MODEL_VERSION = 1

    fun features(
        embedding: FloatArray?,
        hourOfDay: Int,
        senderImportance: Float,
        senderOpenRate: Float,
        focusModeEnabled: Boolean,
        baseScore: Float,
    ): FloatArray? {
        if (embedding == null || embedding.size != EmbeddingCodec.EXPECTED_DIMENSIONS) return null
        val result = FloatArray(FEATURE_COUNT)
        embedding.copyInto(result)
        val phase = 2.0 * PI * hourOfDay.coerceIn(0, 23) / 24.0
        result[384] = sin(phase).toFloat()
        result[385] = cos(phase).toFloat()
        result[386] = senderImportance.coerceIn(0f, 1f) * 2f - 1f
        result[387] = senderOpenRate.coerceIn(0f, 1f) * 2f - 1f
        result[388] = if (focusModeEnabled) 1f else -1f
        result[389] = baseScore.coerceIn(0f, 1f) * 2f - 1f
        return result
    }

    fun predict(state: PersonalizedModelState, features: FloatArray): Float {
        require(features.size == FEATURE_COUNT)
        var logit = state.bias
        for (index in features.indices) {
            logit += state.weights[index] * features[index]
        }
        return sigmoid(logit)
    }

    fun update(
        current: PersonalizedModelState?,
        features: FloatArray,
        important: Boolean,
        predictionBeforeUpdate: Float? = null,
        baselineWasImportant: Boolean? = null,
    ): PersonalizedModelState {
        require(features.size == FEATURE_COUNT)
        val state = current?.takeIf { it.weights.size == FEATURE_COUNT } ?: fresh()
        val prediction = predict(state, features)
        val target = if (important) 0.98f else 0.02f
        val error = target - prediction
        val learningRate = (0.08 / sqrt(1.0 + state.exampleCount / 25.0)).toFloat()
        val weights = state.weights.copyOf()
        for (index in weights.indices) {
            val regularized = weights[index] * L2
            weights[index] += learningRate * (error * features[index] - regularized)
        }
        val personalWasImportant = predictionBeforeUpdate?.let { it >= 0.5f }
        val evaluated = personalWasImportant != null && baselineWasImportant != null
        return state.copy(
            weights = weights,
            bias = (state.bias + learningRate * error).coerceIn(-MAX_LOGIT, MAX_LOGIT),
            positiveCount = state.positiveCount + if (important) 1 else 0,
            negativeCount = state.negativeCount + if (important) 0 else 1,
            evaluationCount = state.evaluationCount + if (evaluated) 1 else 0,
            personalCorrectCount = state.personalCorrectCount +
                if (evaluated && personalWasImportant == important) 1 else 0,
            baselineCorrectCount = state.baselineCorrectCount +
                if (evaluated && baselineWasImportant == important) 1 else 0,
            importantEvaluationCount = state.importantEvaluationCount +
                if (evaluated && important) 1 else 0,
            importantCorrectCount = state.importantCorrectCount +
                if (evaluated && important && personalWasImportant == true) 1 else 0,
            notImportantEvaluationCount = state.notImportantEvaluationCount +
                if (evaluated && !important) 1 else 0,
            falseImportantCount = state.falseImportantCount +
                if (evaluated && !important && personalWasImportant == true) 1 else 0,
        )
    }

    fun fresh(): PersonalizedModelState = PersonalizedModelState(
        weights = FloatArray(FEATURE_COUNT),
        bias = 0f,
        positiveCount = 0,
        negativeCount = 0,
    )

    private fun sigmoid(value: Float): Float {
        val bounded = value.coerceIn(-MAX_LOGIT, MAX_LOGIT)
        return (1.0 / (1.0 + exp(-bounded.toDouble()))).toFloat()
    }

    private const val L2 = 0.0005f
    private const val MAX_LOGIT = 12f
}

data class PersonalizedModelState(
    val weights: FloatArray,
    val bias: Float,
    val positiveCount: Int,
    val negativeCount: Int,
    val evaluationCount: Int = 0,
    val personalCorrectCount: Int = 0,
    val baselineCorrectCount: Int = 0,
    val importantEvaluationCount: Int = 0,
    val importantCorrectCount: Int = 0,
    val notImportantEvaluationCount: Int = 0,
    val falseImportantCount: Int = 0,
) {
    val exampleCount: Int get() = positiveCount + negativeCount
    val personalAccuracy: Float
        get() = if (evaluationCount == 0) 0f else personalCorrectCount.toFloat() / evaluationCount
    val baselineAccuracy: Float
        get() = if (evaluationCount == 0) 0f else baselineCorrectCount.toFloat() / evaluationCount
    val importantRecall: Float
        get() = if (importantEvaluationCount == 0) {
            0f
        } else {
            importantCorrectCount.toFloat() / importantEvaluationCount
        }
    val falseImportantRate: Float
        get() = if (notImportantEvaluationCount == 0) {
            0f
        } else {
            falseImportantCount.toFloat() / notImportantEvaluationCount
        }
    val isActive: Boolean
        get() = exampleCount >= PersonalizedAttentionModel.MIN_EXAMPLES &&
            positiveCount >= PersonalizedAttentionModel.MIN_PER_CLASS &&
            negativeCount >= PersonalizedAttentionModel.MIN_PER_CLASS &&
            evaluationCount >= PersonalizedAttentionModel.MIN_EVALUATIONS &&
            personalAccuracy >= PersonalizedAttentionModel.MIN_ACCURACY &&
            personalCorrectCount >= baselineCorrectCount &&
            importantEvaluationCount >= PersonalizedAttentionModel.MIN_IMPORTANT_EVALUATIONS &&
            importantRecall >= PersonalizedAttentionModel.MIN_IMPORTANT_RECALL
}

data class PersonalizedModelProgress(
    val positiveCount: Int = 0,
    val negativeCount: Int = 0,
    val evaluationCount: Int = 0,
    val personalCorrectCount: Int = 0,
    val baselineCorrectCount: Int = 0,
    val importantEvaluationCount: Int = 0,
    val importantCorrectCount: Int = 0,
    val notImportantEvaluationCount: Int = 0,
    val falseImportantCount: Int = 0,
) {
    val exampleCount: Int get() = positiveCount + negativeCount
    val personalAccuracy: Float
        get() = if (evaluationCount == 0) 0f else personalCorrectCount.toFloat() / evaluationCount
    val baselineAccuracy: Float
        get() = if (evaluationCount == 0) 0f else baselineCorrectCount.toFloat() / evaluationCount
    val importantRecall: Float
        get() = if (importantEvaluationCount == 0) {
            0f
        } else {
            importantCorrectCount.toFloat() / importantEvaluationCount
        }
    val falseImportantRate: Float
        get() = if (notImportantEvaluationCount == 0) {
            0f
        } else {
            falseImportantCount.toFloat() / notImportantEvaluationCount
        }
    val isActive: Boolean
        get() = asState().isActive
    val progressFraction: Float
        get() = (exampleCount.toFloat() / PersonalizedAttentionModel.MIN_EXAMPLES)
            .coerceIn(0f, 1f)

    private fun asState() = PersonalizedModelState(
        weights = FloatArray(PersonalizedAttentionModel.FEATURE_COUNT),
        bias = 0f,
        positiveCount = positiveCount,
        negativeCount = negativeCount,
        evaluationCount = evaluationCount,
        personalCorrectCount = personalCorrectCount,
        baselineCorrectCount = baselineCorrectCount,
        importantEvaluationCount = importantEvaluationCount,
        importantCorrectCount = importantCorrectCount,
        notImportantEvaluationCount = notImportantEvaluationCount,
        falseImportantCount = falseImportantCount,
    )
}

object ModelWeightsCodec {
    fun encode(weights: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(weights.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        weights.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.size != PersonalizedAttentionModel.FEATURE_COUNT * Float.SIZE_BYTES) {
            return null
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(PersonalizedAttentionModel.FEATURE_COUNT) { buffer.getFloat() }
    }
}

object PersonalizedDecisionPolicy {
    fun apply(
        base: AttentionDecision,
        probabilityImportant: Float,
        context: AttentionContext,
        safetyProtected: Boolean,
    ): AttentionDecision {
        val probability = probabilityImportant.coerceIn(0f, 1f)
        val adjustedScore = (
            base.score * BASE_WEIGHT + probability * PERSONAL_WEIGHT
            ).coerceIn(0f, 1f)
        if (safetyProtected && adjustedScore < base.score) return base
        if (abs(adjustedScore - base.score) < MINIMUM_MEANINGFUL_CHANGE) return base

        val adjustedPriority = AttentionPolicy.priorityFor(adjustedScore)
        return base.copy(
            score = adjustedScore,
            priority = adjustedPriority,
            shouldQueue = AttentionPolicy.shouldQueue(context.focusModeEnabled, adjustedPriority),
            explanation = "Your private on-device model adjusted this from your corrections.",
        )
    }

    private const val BASE_WEIGHT = 0.80f
    private const val PERSONAL_WEIGHT = 0.20f
    private const val MINIMUM_MEANINGFUL_CHANGE = 0.015f
}
