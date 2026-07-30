package com.attentionos.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.attentionos.BuildConfig
import com.attentionos.domain.KeywordLanguageAnalyzer
import com.attentionos.domain.LanguageAnalysis
import com.attentionos.domain.LanguageAnalyzer
import com.attentionos.domain.NotificationCategory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Static token-embedding encoder: `static-similarity-mrl-multilingual-v1`, truncated to 128
 * dimensions.
 *
 * There is no transformer here. Each token has one pretrained vector and a sentence is the mean
 * of its tokens, so an embedding costs a few thousand additions instead of a forward pass. That
 * is what lets the app ship with no inference runtime at all.
 *
 * Multilingual by vocabulary rather than by heuristic: 105,879 WordPiece tokens covering Latin,
 * Han, Devanagari, Arabic, Cyrillic, Hangul and Kana, against the 29,528 English-only tokens
 * this replaced. Measured on real notifications, the unknown-token rate for Hindi, Urdu, Chinese
 * and Spanish went to 0.0% from as high as 68%.
 *
 * The model is Matryoshka-trained, so its leading dimensions stand alone. 128 outscored 256 on
 * the labelled set while halving both the asset and the per-notification arithmetic, so the
 * cheaper truncation is also the better one.
 *
 * It remains a real trade: a bag of token vectors cannot represent word order, so "the payment
 * failed" and "failed the payment" embed identically. docs/MODEL_STRATEGY.md records what that
 * costs.
 */
/**
 * Which table an analyzer reads, so candidates can be compared without editing constants.
 *
 * [assets] names the [android.content.res.AssetManager] the files live in — the shipped encoder
 * comes from the app's own assets, while a bake-off candidate is packaged with the test.
 */
data class EncoderAsset(
    val tablePath: String,
    val vocabPath: String,
    val dimensions: Int,
    val version: String,
    val fromTestAssets: Boolean = false,
)

class StaticEmbeddingAnalyzer(
    private val context: Context,
    private val fallback: KeywordLanguageAnalyzer = KeywordLanguageAnalyzer(),
    private val asset: EncoderAsset = SHIPPED,
) : LanguageAnalyzer {
    private val loadLock = Any()

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

        embeddingCache[content]?.let { return analysisFrom(it, fallbackResult) }

        val semantic = runCatching {
            val active = state() ?: return@runCatching null
            val startedAt = SystemClock.elapsedRealtime()
            val embedding = active.embed(content) ?: return@runCatching null

            // Nearest prototype wins outright. There used to be an absolute cosine cutoff of
            // 0.22 below which the category was discarded, which was a number calibrated against
            // one embedding space: moving to a different table changed how cosines distribute and
            // silently stopped security alerts being recognised as SECURITY, which cost them
            // their safety floor. The prototype set already contains OTHER, so the comparison
            // normalises itself — if nothing fits, "a general notification" is the nearest.
            var categoryMatch = NotificationCategory.OTHER
            var categoryScore = -2f
            active.categoryEmbeddings.forEach { (category, prototype) ->
                val score = cosineOf(embedding, prototype)
                if (score > categoryScore) {
                    categoryScore = score
                    categoryMatch = category
                }
            }
            val urgentSimilarity = cosineOf(embedding, active.urgentEmbedding)
            val routineSimilarity = cosineOf(embedding, active.routineEmbedding)
            val semanticUrgency = (
                0.45f + (urgentSimilarity - routineSimilarity) * URGENCY_SPREAD
                ).coerceIn(0f, 1f)

            SemanticResult(
                embedding = embedding,
                category = categoryMatch,
                urgency = semanticUrgency,
            ).also {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        LOG_TAG,
                        "potion inference in ${SystemClock.elapsedRealtime() - startedAt}ms",
                    )
                }
            }
        }.getOrNull() ?: return fallbackResult

        embeddingCache.put(content, semantic)
        return analysisFrom(semantic, fallbackResult)
    }

    /** Semantic urgency may raise the keyword urgency, never lower it. */
    private fun analysisFrom(
        semantic: SemanticResult,
        fallbackResult: LanguageAnalysis,
    ): LanguageAnalysis = LanguageAnalysis(
        urgency = maxOf(fallbackResult.urgency, semantic.urgency),
        category = semantic.category ?: fallbackResult.category,
        semanticEmbedding = semantic.embedding,
        modelVersion = asset.version,
    )

    private class SemanticResult(
        val embedding: FloatArray,
        val category: NotificationCategory?,
        val urgency: Float,
    )

    private fun state(): RuntimeState? {
        runtime?.let { return it }
        if (unavailable) return null
        return synchronized(loadLock) {
            runtime?.let { return@synchronized it }
            runCatching { createRuntime() }
                .onFailure { failure ->
                    unavailable = true
                    Log.w(LOG_TAG, "potion unavailable; falling back to keyword analysis", failure)
                }
                .getOrNull()
                ?.also { runtime = it }
        }
    }

    /**
     * Bake-off candidates are packaged in the instrumentation APK rather than the app, so a
     * candidate's files are read from that context's assets instead.
     */
    private fun assets(): android.content.res.AssetManager =
        if (asset.fromTestAssets) {
            context.packageManager
                .getResourcesForApplication("${context.packageName}.test")
                .assets
        } else {
            context.assets
        }

    private fun createRuntime(): RuntimeState {
        val startedAt = SystemClock.elapsedRealtime()
        val vocabulary = assets().open(asset.vocabPath).bufferedReader().use { it.readLines() }
        val table = readTable()
        require(table.vocabularySize == vocabulary.size) {
            "table has ${table.vocabularySize} rows but the vocabulary has ${vocabulary.size}"
        }
        val tokenizer = WordPieceTokenizer(vocabulary)

        val state = RuntimeState(tokenizer, table)
        // Prototypes are embedded once at load. These are the sentences the transformer
        // encoder used too, so the bake-off compared like with like.
        state.urgentEmbedding = state.embed(URGENT_PROTOTYPE)!!
        state.routineEmbedding = state.embed(ROUTINE_PROTOTYPE)!!
        state.categoryEmbeddings = categoryPrototypes
            .mapValues { (_, prompt) -> state.embed(prompt)!! }

        Log.i(LOG_TAG, "${asset.version} ready in ${SystemClock.elapsedRealtime() - startedAt}ms")
        return state
    }

    /**
     * Maps the quantised table straight out of the APK.
     *
     * The asset is stored uncompressed so this is a mapping rather than a 7 MB read into the
     * Java heap; the rows are touched sparsely, a few per notification.
     */
    private fun readTable(): QuantizedTable {
        assets().openFd(asset.tablePath).use { descriptor ->
            descriptor.createInputStream().use { stream ->
                val channel = stream.channel
                val mapped = channel
                    .map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
                    .order(ByteOrder.LITTLE_ENDIAN)

                val magic = ByteArray(MAGIC.size).also(mapped::get)
                require(magic.contentEquals(MAGIC)) { "not a potion table" }
                val vocabularySize = mapped.int
                val dimensions = mapped.int
                require(dimensions == asset.dimensions) {
                    "table is ${dimensions}-dimensional, expected ${asset.dimensions}"
                }

                val scales = FloatArray(vocabularySize)
                mapped.asFloatBuffer().get(scales)
                mapped.position(mapped.position() + vocabularySize * Float.SIZE_BYTES)

                val weights = mapped.slice()
                require(weights.remaining() == vocabularySize * dimensions) {
                    "table is truncated: ${weights.remaining()} bytes for " +
                        "$vocabularySize x $dimensions"
                }
                return QuantizedTable(vocabularySize, dimensions, scales, weights)
            }
        }
    }

    /** Row-wise symmetric int8 quantisation: `value = weight * scale[row]`. */
    private class QuantizedTable(
        val vocabularySize: Int,
        val dimensions: Int,
        private val scales: FloatArray,
        private val weights: ByteBuffer,
    ) {
        /** Adds row [id] into [into]. */
        fun accumulate(id: Int, into: FloatArray) {
            val scale = scales[id]
            val base = id * dimensions
            for (index in 0 until dimensions) {
                into[index] += weights.get(base + index) * scale
            }
        }
    }

    private class RuntimeState(
        private val tokenizer: WordPieceTokenizer,
        private val table: QuantizedTable,
    ) {
        lateinit var urgentEmbedding: FloatArray
        lateinit var routineEmbedding: FloatArray
        lateinit var categoryEmbeddings: Map<NotificationCategory, FloatArray>

        /**
         * Mean of the token vectors, L2-normalised.
         *
         * No `[CLS]`/`[SEP]`: model2vec distils per-token vectors and pools them directly, so
         * adding the transformer's sentence markers would average in two vectors that carry no
         * meaning here.
         */
        fun embed(text: String): FloatArray? {
            val ids = tokenizer.encodePieces(text, MAX_TOKENS)
            if (ids.isEmpty()) return null

            val sum = FloatArray(table.dimensions)
            ids.forEach { id -> table.accumulate(id, sum) }
            val inverse = 1f / ids.size
            for (index in sum.indices) sum[index] *= inverse
            return normalizeInPlace(sum)
        }
    }

    internal companion object {
        fun modelVersion(): String = MODEL_VERSION

        /** The encoder the app ships with. */
        val SHIPPED = EncoderAsset(
            tablePath = TABLE_ASSET,
            vocabPath = VOCAB_ASSET,
            dimensions = EMBEDDING_SIZE,
            version = MODEL_VERSION,
        )

        const val TABLE_ASSET = "models/multilingual-128d-q8.bin"
        const val VOCAB_ASSET = "models/multilingual-128d-vocab.txt"
        const val MODEL_VERSION = "static-mrl-multilingual-128d-q8"
        const val EMBEDDING_SIZE = 128
        const val LOG_TAG = "AttentionAI"
        const val EMBEDDING_CACHE_ENTRIES = 64

        /**
         * Generous compared with the transformer's 64: there is no quadratic attention cost, so
         * truncating early would discard signal for no saving.
         */
        const val MAX_TOKENS = 256

        /**
         * Static embeddings are less spread out than transformer ones, so the same raw gap
         * between the urgent and routine prototypes means more. Calibrated on the labelled set.
         */
        const val URGENCY_SPREAD = 1.35f

        val MAGIC = byteArrayOf('P'.code.toByte(), '2'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())

        val EXPECTED_VOCABULARY_SIZE = 105_879

        const val URGENT_PROTOTYPE =
            "This is an emergency requiring immediate action and a fast response."
        const val ROUTINE_PROTOTYPE =
            "This is a routine informational update that can be read later."

        val categoryPrototypes = mapOf(
            NotificationCategory.SECURITY to
                "Security warning, suspicious sign-in from a new device, verification or " +
                    "one-time code, password or two-factor change, fraud, or a locked account.",
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

private fun cosineOf(left: FloatArray, right: FloatArray): Float {
    var dot = 0f
    var leftMagnitude = 0f
    var rightMagnitude = 0f
    val length = minOf(left.size, right.size)
    for (index in 0 until length) {
        dot += left[index] * right[index]
        leftMagnitude += left[index] * left[index]
        rightMagnitude += right[index] * right[index]
    }
    val denominator = kotlin.math.sqrt(leftMagnitude) * kotlin.math.sqrt(rightMagnitude)
    return if (denominator == 0f) 0f else dot / denominator
}

private fun normalizeInPlace(values: FloatArray): FloatArray {
    var magnitude = 0f
    values.forEach { magnitude += it * it }
    val divisor = kotlin.math.sqrt(magnitude)
    if (divisor > 0f) values.indices.forEach { values[it] /= divisor }
    return values
}
