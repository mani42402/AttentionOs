package com.attentionos.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import android.util.Log
import com.attentionos.BuildConfig
import com.attentionos.domain.KeywordLanguageAnalyzer
import com.attentionos.domain.LanguageAnalysis
import com.attentionos.domain.LanguageAnalyzer
import com.attentionos.domain.NotificationCategory
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Real on-device transformer inference using the pretrained, INT8-quantized
 * sentence-transformers/paraphrase-MiniLM-L3-v2 model.
 *
 * The model creates a 384-dimensional semantic embedding. Notification meaning is resolved by
 * cosine similarity to embedded attention prototypes. The deterministic analyzer is retained only
 * as a safety net and for explicit high-risk phrases.
 */
class MiniLmLanguageAnalyzer(
    private val context: Context,
    private val fallback: KeywordLanguageAnalyzer = KeywordLanguageAnalyzer(),
) : LanguageAnalyzer {
    private val inferenceLock = Any()

    /**
     * Recently embedded notification text. Bounded: each entry holds a 384-float embedding
     * (~1.5KB), so the cache costs roughly 100KB at capacity.
     */
    private val embeddingCache = LruCache<String, SemanticResult>(EMBEDDING_CACHE_ENTRIES)

    @Volatile
    private var runtime: RuntimeState? = null

    @Volatile
    private var unavailable = false

    fun warmUp() {
        state()
    }

    override fun analyze(title: String?, text: String?, packageName: String): LanguageAnalysis {
        val fallbackResult = fallback.analyze(title, text, packageName)
        val content = listOfNotNull(title, text).joinToString(" ").trim()
        if (content.isEmpty()) return fallbackResult

        // Apps update the same notification repeatedly (progress, message counts, "typing…"),
        // and each update previously paid for a full transformer pass over identical text.
        // The embedding is a pure function of the content, so it is safe to reuse.
        embeddingCache[content]?.let { cached ->
            return analysisFrom(cached, fallbackResult)
        }

        val semantic = runCatching {
            val active = state() ?: return@runCatching null
            synchronized(inferenceLock) {
                val startedAt = SystemClock.elapsedRealtime()
                val embedding = active.embed(content)
                // Score each prototype once; the previous form re-scored the winner.
                var categoryMatch: NotificationCategory? = null
                var categoryScore = -1f
                active.categoryEmbeddings.forEach { (category, prototype) ->
                    val score = cosine(embedding, prototype)
                    if (score > categoryScore) {
                        categoryScore = score
                        categoryMatch = category
                    }
                }
                val urgentSimilarity = cosine(embedding, active.urgentEmbedding)
                val routineSimilarity = cosine(embedding, active.routineEmbedding)
                val semanticUrgency = (
                    0.45f + (urgentSimilarity - routineSimilarity) * 1.35f
                    ).coerceIn(0f, 1f)

                SemanticResult(
                    embedding = embedding,
                    category = categoryMatch?.takeIf { categoryScore >= CATEGORY_THRESHOLD },
                    urgency = semanticUrgency,
                ).also {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            LOG_TAG,
                            "MiniLM inference completed in " +
                                "${SystemClock.elapsedRealtime() - startedAt}ms",
                        )
                    }
                }
            }
        }.getOrNull() ?: return fallbackResult

        embeddingCache.put(content, semantic)
        return analysisFrom(semantic, fallbackResult)
    }

    /**
     * Combines cached model output with this call's keyword result.
     *
     * Only the model half is cached: the keyword analyzer also considers the package name, so
     * the same text from a different app can legitimately produce a different fallback.
     * Semantic urgency can only raise the keyword urgency, never lower it.
     */
    private fun analysisFrom(
        semantic: SemanticResult,
        fallbackResult: LanguageAnalysis,
    ): LanguageAnalysis = LanguageAnalysis(
        urgency = maxOf(fallbackResult.urgency, semantic.urgency),
        category = semantic.category ?: fallbackResult.category,
        semanticEmbedding = semantic.embedding,
        modelVersion = MODEL_VERSION,
    )

    /** Model-derived facts about a piece of text, independent of which app sent it. */
    private class SemanticResult(
        val embedding: FloatArray,
        val category: NotificationCategory?,
        val urgency: Float,
    )

    private fun state(): RuntimeState? {
        runtime?.let { return it }
        if (unavailable) return null
        return synchronized(inferenceLock) {
            runtime?.let { return@synchronized it }
            runCatching { createRuntime() }
                .onFailure { failure ->
                    unavailable = true
                    // Previously swallowed. A silent disable means the app reports keyword
                    // results as though the model ran, with no way to tell from a bug report.
                    Log.w(LOG_TAG, "MiniLM unavailable; falling back to keyword analysis", failure)
                }
                .getOrNull()
                ?.also { runtime = it }
        }
    }

    private fun createRuntime(): RuntimeState {
        val startedAt = SystemClock.elapsedRealtime()
        val modelFile = copyModelToPrivateStorage()
        val tokenizer = WordPieceTokenizer(
            context.assets.open(VOCAB_ASSET).bufferedReader().use { it.readLines() },
        )
        check(tokenizer.vocabularySize == EXPECTED_VOCABULARY_SIZE) {
            "MiniLM vocabulary has ${tokenizer.vocabularySize} entries, " +
                "expected $EXPECTED_VOCABULARY_SIZE"
        }

        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Cache the optimized graph so ALL_OPT is not re-run on every process start.
            runCatching { setOptimizedModelFilePath(optimizedModelFile().absolutePath) }
        }
        val session = environment.createSession(modelFile.absolutePath, options)
        val base = RuntimeState(
            environment = environment,
            session = session,
            tokenizer = tokenizer,
            dynamicSequence = hasDynamicSequenceAxis(session),
        )

        return base.copy(
            categoryEmbeddings = categoryPrototypes.mapValues { base.embed(it.value) },
            urgentEmbedding = base.embed(URGENT_PROTOTYPE),
            routineEmbedding = base.embed(ROUTINE_PROTOTYPE),
        ).also {
            Log.i(
                LOG_TAG,
                "MiniLM ready in ${SystemClock.elapsedRealtime() - startedAt}ms " +
                    "(dynamicSequence=${base.dynamicSequence})",
            )
        }
    }

    /**
     * True when the model's sequence axis is symbolic, which lets us size each forward pass to
     * the actual token count. Detected rather than assumed: if a future model pins the axis,
     * padding to [MAX_TOKENS] remains correct instead of failing at inference time.
     */
    private fun hasDynamicSequenceAxis(session: OrtSession): Boolean = runCatching {
        val info = session.inputInfo["input_ids"]?.info as? TensorInfo
        val shape = info?.shape ?: return false
        shape.size >= 2 && shape[1] <= 0L
    }.getOrDefault(false)

    private fun optimizedModelFile(): File =
        File(context.noBackupFilesDir, "models").apply { mkdirs() }
            .let { File(it, "$MODEL_FILENAME.opt") }

    /**
     * Copies the bundled model into private storage via a temp file and an atomic rename.
     *
     * The previous implementation wrote in place and guarded only on a minimum size, so a copy
     * interrupted between that threshold and the true length left a truncated file that passed
     * the check on every subsequent launch — permanently disabling the model. Verifying the
     * exact length makes a partial copy self-healing.
     */
    private fun copyModelToPrivateStorage(): File {
        val modelDirectory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
        val output = File(modelDirectory, MODEL_FILENAME)
        val expectedBytes = context.assets.openFd(MODEL_ASSET).use { it.length }

        if (output.exists() && output.length() == expectedBytes) return output

        val temporary = File(modelDirectory, "$MODEL_FILENAME.tmp")
        temporary.delete()
        context.assets.open(MODEL_ASSET).use { input ->
            temporary.outputStream().buffered().use { destination ->
                input.copyTo(destination, bufferSize = COPY_BUFFER_BYTES)
                destination.flush()
            }
        }
        check(temporary.length() == expectedBytes) {
            "Model copy is ${temporary.length()} bytes, expected $expectedBytes"
        }
        output.delete()
        check(temporary.renameTo(output)) { "Could not install model file" }
        return output
    }

    private data class RuntimeState(
        val environment: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: WordPieceTokenizer,
        val dynamicSequence: Boolean = false,
        val categoryEmbeddings: Map<NotificationCategory, FloatArray> = emptyMap(),
        val urgentEmbedding: FloatArray = FloatArray(EMBEDDING_SIZE),
        val routineEmbedding: FloatArray = FloatArray(EMBEDDING_SIZE),
    ) {
        fun embed(text: String): FloatArray {
            val encoded = tokenizer.encode(text, MAX_TOKENS)
            // A typical notification is 8-20 tokens. When the model's sequence axis is
            // symbolic, bucket up to the next power of two instead of always padding to 64,
            // which cuts the per-notification forward pass several-fold at no accuracy cost.
            val width = if (dynamicSequence) bucketWidth(encoded.size) else MAX_TOKENS
            val ids = encoded.ids.copyOf(width)
            val mask = encoded.mask.copyOf(width)

            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input_ids"] = tensor(ids)
            inputs["attention_mask"] = tensor(mask)
            if ("token_type_ids" in session.inputNames) {
                inputs["token_type_ids"] = tensor(LongArray(width))
            }

            return try {
                session.run(inputs).use { result ->
                    meanPool(result[0].value, mask)
                }
            } finally {
                inputs.values.forEach(OnnxTensor::close)
            }
        }

        private fun bucketWidth(tokenCount: Int): Int =
            SEQUENCE_BUCKETS.firstOrNull { it >= tokenCount } ?: MAX_TOKENS

        private fun tensor(values: LongArray): OnnxTensor =
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(values),
                longArrayOf(1, values.size.toLong()),
            )

        private fun meanPool(output: Any, mask: LongArray): FloatArray {
            val batch = output as? Array<*> ?: error("Unexpected MiniLM output")
            val first = batch.firstOrNull() ?: error("Empty MiniLM output")
            if (first is FloatArray) return normalize(first)

            val tokenRows = first as? Array<*> ?: error("Unexpected MiniLM token output")
            val pooled = FloatArray((tokenRows.firstOrNull() as? FloatArray)?.size ?: EMBEDDING_SIZE)
            var used = 0
            tokenRows.forEachIndexed { index, row ->
                if (index < mask.size && mask[index] == 1L) {
                    val values = row as FloatArray
                    values.forEachIndexed { dimension, value -> pooled[dimension] += value }
                    used++
                }
            }
            if (used > 0) pooled.indices.forEach { pooled[it] /= used }
            return normalize(pooled)
        }
    }

    private companion object {
        const val MODEL_ASSET = "models/minilm-l3-qint8-arm64.onnx"
        const val VOCAB_ASSET = "models/minilm-vocab.txt"
        const val MODEL_FILENAME = "minilm-l3-qint8-arm64.onnx"
        const val MODEL_VERSION = "paraphrase-MiniLM-L3-v2-qint8-arm64"
        const val MAX_TOKENS = 64
        const val EMBEDDING_SIZE = 384
        const val CATEGORY_THRESHOLD = 0.22f
        const val LOG_TAG = "AttentionAI"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val EMBEDDING_CACHE_ENTRIES = 64

        /** bert-base-uncased vocabulary; a mismatch means the wrong asset shipped. */
        const val EXPECTED_VOCABULARY_SIZE = 30_522

        /** Sequence lengths the encoder is run at, smallest fitting bucket wins. */
        val SEQUENCE_BUCKETS = intArrayOf(16, 32, MAX_TOKENS)

        const val URGENT_PROTOTYPE =
            "This is an emergency requiring immediate action and a fast response."
        const val ROUTINE_PROTOTYPE =
            "This is a routine informational update that can be read later."

        val categoryPrototypes = mapOf(
            NotificationCategory.SECURITY to
                "Security warning, suspicious login, verification code, fraud or locked account.",
            NotificationCategory.FINANCE to
                "Bank transaction, payment, card charge, invoice or account balance.",
            NotificationCategory.WORK to
                "Work project, production incident, manager request, client task or meeting.",
            NotificationCategory.SOCIAL to
                "A personal message, social conversation, friend or family update.",
            NotificationCategory.DELIVERY to
                "Package delivery, courier, driver, food order or shipment arriving.",
            NotificationCategory.PROMOTION to
                "Advertising, marketing sale, discount, coupon, offer or shopping deal.",
            NotificationCategory.SYSTEM to
                "Device system status, software update, battery or operating system message.",
            NotificationCategory.OTHER to
                "A general notification that does not belong to a specific category.",
        )
    }
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

private fun normalize(values: FloatArray): FloatArray {
    var magnitude = 0f
    values.forEach { magnitude += it * it }
    val divisor = sqrt(magnitude)
    if (divisor > 0f) values.indices.forEach { values[it] /= divisor }
    return values
}
