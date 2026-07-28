package com.attentionos.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.attentionos.BuildConfig
import com.attentionos.domain.KeywordLanguageAnalyzer
import com.attentionos.domain.LanguageAnalysis
import com.attentionos.domain.LanguageAnalyzer
import com.attentionos.domain.NotificationCategory
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer
import java.util.Locale
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

        val modelResult = runCatching {
            val active = state() ?: return@runCatching null
            synchronized(inferenceLock) {
                val startedAt = SystemClock.elapsedRealtime()
                val embedding = active.embed(content)
                val categoryMatch = active.categoryEmbeddings
                    .maxByOrNull { (_, prototype) -> cosine(embedding, prototype) }
                val categoryScore = categoryMatch?.let { cosine(embedding, it.value) } ?: -1f
                val urgentSimilarity = cosine(embedding, active.urgentEmbedding)
                val routineSimilarity = cosine(embedding, active.routineEmbedding)
                val semanticUrgency = (
                    0.45f + (urgentSimilarity - routineSimilarity) * 1.35f
                    ).coerceIn(0f, 1f)

                LanguageAnalysis(
                    urgency = maxOf(fallbackResult.urgency, semanticUrgency),
                    category = if (categoryScore >= CATEGORY_THRESHOLD) {
                        categoryMatch!!.key
                    } else {
                        fallbackResult.category
                    },
                    semanticEmbedding = embedding,
                    modelVersion = MODEL_VERSION,
                ).also {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "MiniLM inference completed in " +
                            "${SystemClock.elapsedRealtime() - startedAt}ms")
                    }
                }
            }
        }.getOrNull()

        return modelResult ?: fallbackResult
    }

    private fun state(): RuntimeState? {
        runtime?.let { return it }
        if (unavailable) return null
        return synchronized(inferenceLock) {
            runtime?.let { return@synchronized it }
            runCatching { createRuntime() }
                .onFailure { unavailable = true }
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
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val session = environment.createSession(modelFile.absolutePath, options)
        val base = RuntimeState(environment, session, tokenizer)

        return base.copy(
            categoryEmbeddings = categoryPrototypes.mapValues { base.embed(it.value) },
            urgentEmbedding = base.embed(URGENT_PROTOTYPE),
            routineEmbedding = base.embed(ROUTINE_PROTOTYPE),
        ).also {
            Log.i(
                LOG_TAG,
                "MiniLM transformer ready in ${SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }
    }

    private fun copyModelToPrivateStorage(): File {
        val modelDirectory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
        val output = File(modelDirectory, MODEL_FILENAME)
        if (!output.exists() || output.length() < MIN_MODEL_BYTES) {
            context.assets.open(MODEL_ASSET).use { input ->
                output.outputStream().buffered().use { destination ->
                    input.copyTo(destination, bufferSize = 64 * 1024)
                }
            }
        }
        return output
    }

    private data class RuntimeState(
        val environment: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: WordPieceTokenizer,
        val categoryEmbeddings: Map<NotificationCategory, FloatArray> = emptyMap(),
        val urgentEmbedding: FloatArray = FloatArray(EMBEDDING_SIZE),
        val routineEmbedding: FloatArray = FloatArray(EMBEDDING_SIZE),
    ) {
        fun embed(text: String): FloatArray {
            val tokens = tokenizer.encode(text, MAX_TOKENS)
            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input_ids"] = tensor(tokens.ids)
            inputs["attention_mask"] = tensor(tokens.mask)
            if ("token_type_ids" in session.inputNames) {
                inputs["token_type_ids"] = tensor(LongArray(MAX_TOKENS))
            }

            return try {
                session.run(inputs).use { result ->
                    meanPool(result[0].value, tokens.mask)
                }
            } finally {
                inputs.values.forEach(OnnxTensor::close)
            }
        }

        private fun tensor(values: LongArray): OnnxTensor =
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(values),
                longArrayOf(1, MAX_TOKENS.toLong()),
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

    private class WordPieceTokenizer(vocabulary: List<String>) {
        private val tokenIds = vocabulary.withIndex().associate { it.value to it.index.toLong() }
        private val unknown = tokenIds["[UNK]"] ?: 100L
        private val classification = tokenIds["[CLS]"] ?: 101L
        private val separator = tokenIds["[SEP]"] ?: 102L

        fun encode(text: String, maxTokens: Int): EncodedTokens {
            val pieces = basicTokens(text).flatMap(::wordPieces)
            val ids = LongArray(maxTokens)
            val mask = LongArray(maxTokens)
            var index = 0
            ids[index] = classification
            mask[index++] = 1
            pieces.take(maxTokens - 2).forEach { piece ->
                ids[index] = tokenIds[piece] ?: unknown
                mask[index++] = 1
            }
            ids[index] = separator
            mask[index] = 1
            return EncodedTokens(ids, mask)
        }

        private fun basicTokens(input: String): List<String> {
            val normalized = Normalizer.normalize(
                input.lowercase(Locale.ROOT),
                Normalizer.Form.NFD,
            ).replace(Regex("\\p{Mn}+"), "")
            return TOKEN_PATTERN.findAll(normalized).map { it.value }.toList()
        }

        private fun wordPieces(token: String): List<String> {
            if (token.length > 100) return listOf("[UNK]")
            val output = mutableListOf<String>()
            var start = 0
            while (start < token.length) {
                var end = token.length
                var match: String? = null
                while (start < end) {
                    val candidate = (if (start == 0) "" else "##") + token.substring(start, end)
                    if (candidate in tokenIds) {
                        match = candidate
                        break
                    }
                    end--
                }
                if (match == null) return listOf("[UNK]")
                output += match
                start = end
            }
            return output
        }
    }

    private data class EncodedTokens(val ids: LongArray, val mask: LongArray)

    private companion object {
        const val MODEL_ASSET = "models/minilm-l3-qint8-arm64.onnx"
        const val VOCAB_ASSET = "models/minilm-vocab.txt"
        const val MODEL_FILENAME = "minilm-l3-qint8-arm64.onnx"
        const val MODEL_VERSION = "paraphrase-MiniLM-L3-v2-qint8-arm64"
        const val MIN_MODEL_BYTES = 16_000_000L
        const val MAX_TOKENS = 64
        const val EMBEDDING_SIZE = 384
        const val CATEGORY_THRESHOLD = 0.22f
        const val LOG_TAG = "AttentionAI"
        val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")

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
