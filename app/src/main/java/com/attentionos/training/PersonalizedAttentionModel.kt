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
    /**
     * Feature-space version.
     *
     * Bump whenever the embedding that feeds the classifier changes. Weights learned against a
     * different encoder describe a different space entirely, so reusing them would silently
     * produce nonsense rather than fail. A mismatch discards the stored model and relearns.
     *
     * v2: encoder moved from paraphrase-MiniLM-L3-v2 to all-MiniLM-L6-v2.
     */
    /**
     * 3: the encoder moved from a 384-dimensional transformer to a 256-dimensional static
     * table, so every stored weight vector describes a feature space that no longer exists.
     */
    const val MODEL_VERSION = 3

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
        // Context features follow the embedding. Indexed off the embedding width rather than
        // written as literals, so a change of encoder cannot silently corrupt the tail.
        val context = EmbeddingCodec.EXPECTED_DIMENSIONS
        val phase = 2.0 * PI * hourOfDay.coerceIn(0, 23) / 24.0
        result[context] = sin(phase).toFloat()
        result[context + 1] = cos(phase).toFloat()
        result[context + 2] = senderImportance.coerceIn(0f, 1f) * 2f - 1f
        result[context + 3] = senderOpenRate.coerceIn(0f, 1f) * 2f - 1f
        result[context + 4] = if (focusModeEnabled) 1f else -1f
        result[context + 5] = baseScore.coerceIn(0f, 1f) * 2f - 1f
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

    /**
     * Refits the classifier from scratch over every stored correction.
     *
     * [update] takes exactly one gradient step per correction, ever: the third example is seen
     * once, at a large learning rate, and never revisited. That systematically underfits and
     * makes the result depend on the order corrections happened to arrive. Because the
     * embeddings are already persisted, the whole set can simply be re-fit — 500 examples over
     * 30 epochs is about 6M multiply-adds, a few milliseconds of Kotlin.
     *
     * Classes are weighted by inverse frequency. That is the correct imbalance fix here:
     * resampling would leave accuracy unchanged while degrading calibration, and calibration is
     * exactly what matters because the probability is blended into a score and re-bucketed.
     *
     * Shuffling uses a fixed seed so a given correction set always produces the same model,
     * which keeps behaviour reproducible and testable.
     */
    fun refit(
        samples: List<TrainingSample>,
        counters: PersonalizedModelState? = null,
        epochs: Int = REFIT_EPOCHS,
    ): PersonalizedModelState {
        require(samples.all { it.features.size == FEATURE_COUNT })
        val positives = samples.count { it.important }
        val negatives = samples.size - positives
        if (positives == 0 || negatives == 0) {
            // A single-class set has no decision boundary to learn; keep the counters so the
            // UI can still show progress toward the balance requirement.
            return (counters ?: fresh()).copy(
                weights = FloatArray(FEATURE_COUNT),
                bias = 0f,
                positiveCount = positives,
                negativeCount = negatives,
            )
        }

        val positiveWeight = samples.size / (2f * positives)
        val negativeWeight = samples.size / (2f * negatives)
        val weights = FloatArray(FEATURE_COUNT)
        var bias = 0f
        val order = samples.indices.toMutableList()
        val random = java.util.Random(REFIT_SEED)

        for (epoch in 0 until epochs) {
            java.util.Collections.shuffle(order, random)
            // Decays across epochs so later passes settle rather than bounce.
            val learningRate = (REFIT_LEARNING_RATE / (1.0 + epoch * 0.15)).toFloat()
            for (index in order) {
                val sample = samples[index]
                var logit = bias
                for (feature in 0 until FEATURE_COUNT) {
                    logit += weights[feature] * sample.features[feature]
                }
                val prediction = sigmoid(logit)
                val target = if (sample.important) 0.98f else 0.02f
                val classWeight = if (sample.important) positiveWeight else negativeWeight
                val error = (target - prediction) * classWeight
                for (feature in 0 until FEATURE_COUNT) {
                    weights[feature] += learningRate *
                        (error * sample.features[feature] - weights[feature] * L2)
                }
                bias = (bias + learningRate * error).coerceIn(-MAX_LOGIT, MAX_LOGIT)
            }
        }

        return (counters ?: fresh()).copy(
            weights = weights,
            bias = bias,
            positiveCount = positives,
            negativeCount = negatives,
        )
    }

    /**
     * Class centroids over the raw embeddings, used before the classifier has enough data.
     *
     * A nearest-class-mean is stable from a handful of examples, where logistic regression is
     * still noise. Without it the personal model stays inert until 50 corrections plus the
     * evaluation gates — a bar most users never reach, which made the "it learns from you"
     * promise theoretical for them.
     */
    fun centroids(samples: List<TrainingSample>): Centroids? {
        val important = samples.filter { it.important }
        val notImportant = samples.filterNot { it.important }
        if (important.size < MIN_PROTOTYPE_PER_CLASS ||
            notImportant.size < MIN_PROTOTYPE_PER_CLASS
        ) {
            return null
        }
        return Centroids(
            important = meanEmbedding(important),
            notImportant = meanEmbedding(notImportant),
        )
    }

    private fun meanEmbedding(samples: List<TrainingSample>): FloatArray {
        val mean = FloatArray(EmbeddingCodec.EXPECTED_DIMENSIONS)
        for (sample in samples) {
            for (index in mean.indices) mean[index] += sample.features[index]
        }
        for (index in mean.indices) mean[index] /= samples.size
        return mean
    }

    /**
     * Probability-like score from centroid similarity, in the same 0..1 range as [predict] so
     * the two can be blended.
     */
    fun prototypeScore(embedding: FloatArray, centroids: Centroids): Float {
        val toImportant = cosine(embedding, centroids.important)
        val toNotImportant = cosine(embedding, centroids.notImportant)
        return sigmoid((toImportant - toNotImportant) * PROTOTYPE_SHARPNESS)
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftMagnitude = 0f
        var rightMagnitude = 0f
        val length = minOf(left.size, right.size)
        for (index in 0 until length) {
            dot += left[index] * right[index]
            leftMagnitude += left[index] * left[index]
            rightMagnitude += right[index] * right[index]
        }
        val denominator = sqrt(leftMagnitude) * sqrt(rightMagnitude)
        return if (denominator == 0f) 0f else dot / denominator
    }

    private fun sigmoid(value: Float): Float {
        val bounded = value.coerceIn(-MAX_LOGIT, MAX_LOGIT)
        return (1.0 / (1.0 + exp(-bounded.toDouble()))).toFloat()
    }

    private const val L2 = 0.0005f
    private const val MAX_LOGIT = 12f

    /** Enough per class for a centroid to mean something without waiting for the classifier. */
    const val MIN_PROTOTYPE_PER_CLASS = 3
    private const val REFIT_EPOCHS = 30
    private const val REFIT_LEARNING_RATE = 0.05
    private const val REFIT_SEED = 20260729L
    /** Maps a cosine gap of ~0.2 onto a clearly-decided probability. */
    private const val PROTOTYPE_SHARPNESS = 8f
}

/** One stored correction: the feature vector that produced it and the label the user gave. */
data class TrainingSample(val features: FloatArray, val important: Boolean) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TrainingSample && important == other.important &&
                features.contentEquals(other.features))

    override fun hashCode(): Int = 31 * features.contentHashCode() + important.hashCode()
}

/** Class means over the embedding half of the feature vector. */
data class Centroids(val important: FloatArray, val notImportant: FloatArray) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Centroids && important.contentEquals(other.important) &&
                notImportant.contentEquals(other.notImportant))

    override fun hashCode(): Int = 31 * important.contentHashCode() + notImportant.contentHashCode()
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
    /**
     * Enough corrections in both classes for centroids to be meaningful.
     *
     * Far lower than [isActive]'s bar, because a class mean needs a handful of points where a
     * 390-weight classifier needs dozens.
     */
    val hasPrototypeEvidence: Boolean
        get() = positiveCount >= PersonalizedAttentionModel.MIN_PROTOTYPE_PER_CLASS &&
            negativeCount >= PersonalizedAttentionModel.MIN_PROTOTYPE_PER_CLASS

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
    /**
     * Enough corrections in both classes for centroids to be meaningful.
     *
     * Far lower than [isActive]'s bar, because a class mean needs a handful of points where a
     * 390-weight classifier needs dozens.
     */
    val hasPrototypeEvidence: Boolean
        get() = positiveCount >= PersonalizedAttentionModel.MIN_PROTOTYPE_PER_CLASS &&
            negativeCount >= PersonalizedAttentionModel.MIN_PROTOTYPE_PER_CLASS

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
    /** Encodes an arbitrary-length float vector, used for the class centroids. */
    fun encodeVector(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    /** Decodes a vector written by [encodeVector]; null when the length is not a whole float count. */
    fun decodeVector(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.isEmpty() || bytes.size % Float.SIZE_BYTES != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat() }
    }

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
