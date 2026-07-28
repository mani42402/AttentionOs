package com.attentionos.ai

import java.text.Normalizer
import java.util.Locale

/**
 * WordPiece tokenizer matching HuggingFace `BertTokenizer` with
 * `do_lower_case=true, strip_accents=true`, as used by the bundled MiniLM model.
 *
 * Extracted from the analyzer so it can be tested directly: this is the highest-risk logic in
 * the app, because a tokenization mismatch does not fail loudly — it silently produces a
 * meaningless embedding, and every downstream decision degrades without any error surfacing.
 *
 * Tests pin the output against golden vectors generated from the reference Python tokenizer.
 */
internal class WordPieceTokenizer(vocabulary: List<String>) {

    private val tokenIds: Map<String, Long>
    private val unknown: Long
    private val classification: Long
    private val separator: Long

    init {
        require(vocabulary.isNotEmpty()) { "MiniLM vocabulary is empty" }

        // `associate` keeps the LAST occurrence on collision, so a duplicated or blank line
        // would silently shift token ids and corrupt every embedding. Fail loudly instead.
        val seen = HashMap<String, Long>(vocabulary.size * 2)
        vocabulary.forEachIndexed { index, token ->
            require(token.isNotEmpty()) { "MiniLM vocabulary has a blank entry at line ${index + 1}" }
            val previous = seen.put(token, index.toLong())
            require(previous == null) {
                "MiniLM vocabulary has duplicate token '$token' at lines ${previous!! + 1} and ${index + 1}"
            }
        }
        tokenIds = seen

        unknown = requireNotNull(tokenIds[UNK]) { "MiniLM vocabulary is missing $UNK" }
        classification = requireNotNull(tokenIds[CLS]) { "MiniLM vocabulary is missing $CLS" }
        separator = requireNotNull(tokenIds[SEP]) { "MiniLM vocabulary is missing $SEP" }
    }

    /** Number of entries in the loaded vocabulary. Exposed for integrity assertions. */
    val vocabularySize: Int get() = tokenIds.size

    /**
     * Encodes [text] as `[CLS] … [SEP]`, truncated to [maxTokens].
     *
     * The returned arrays are sized to the actual token count rather than padded to
     * [maxTokens]: the ONNX model declares a dynamic sequence axis, so a short notification
     * costs a correspondingly short forward pass. Padding every input to 64 made an 8-token
     * notification cost the same as a 64-token one.
     */
    fun encode(text: String, maxTokens: Int): EncodedTokens {
        require(maxTokens >= 2) { "maxTokens must leave room for [CLS] and [SEP]" }
        val pieces = basicTokens(text)
            .flatMap(::wordPieces)
            .take(maxTokens - 2)

        val length = pieces.size + 2
        val ids = LongArray(length)
        val mask = LongArray(length) { 1L }

        ids[0] = classification
        pieces.forEachIndexed { index, piece ->
            ids[index + 1] = tokenIds[piece] ?: unknown
        }
        ids[length - 1] = separator
        return EncodedTokens(ids, mask)
    }

    /**
     * Lowercases, strips accents, isolates CJK characters, then splits into words and
     * punctuation.
     */
    private fun basicTokens(input: String): List<String> {
        val normalized = Normalizer.normalize(
            input.lowercase(Locale.ROOT),
            Normalizer.Form.NFD,
        ).replace(COMBINING_MARKS, "")
        return TOKEN_PATTERN.findAll(padCjk(normalized)).map { it.value }.toList()
    }

    /**
     * Surrounds every CJK ideograph with spaces, matching BERT's `_tokenize_chinese_chars`.
     *
     * Without this the word pattern groups a whole Chinese or Japanese sentence into a single
     * "word", WordPiece fails to segment it, and the entire notification collapses to
     * `[CLS] [UNK] [SEP]` — i.e. every CJK notification embeds identically and carries no
     * meaning at all.
     */
    private fun padCjk(text: String): String {
        if (text.none { isCjk(it.code) }) return text
        return buildString(text.length * 2) {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                if (isCjk(codePoint)) {
                    append(' ')
                    appendRange(text, index, index + charCount)
                    append(' ')
                } else {
                    appendRange(text, index, index + charCount)
                }
                index += charCount
            }
        }
    }

    private fun isCjk(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0x2F800..0x2FA1F

    /** Greedy longest-match-first segmentation; the whole token becomes [UNK] on failure. */
    private fun wordPieces(token: String): List<String> {
        if (token.length > MAX_CHARS_PER_TOKEN) return listOf(UNK)
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
            if (match == null) return listOf(UNK)
            output += match
            start = end
        }
        return output
    }

    internal companion object {
        const val UNK = "[UNK]"
        const val CLS = "[CLS]"
        const val SEP = "[SEP]"

        /** HuggingFace uses this same cutoff before giving up on a token. */
        const val MAX_CHARS_PER_TOKEN = 100

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")
    }
}

internal data class EncodedTokens(val ids: LongArray, val mask: LongArray) {
    val size: Int get() = ids.size

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EncodedTokens &&
                    ids.contentEquals(other.ids) &&
                    mask.contentEquals(other.mask)
                )

    override fun hashCode(): Int = 31 * ids.contentHashCode() + mask.contentHashCode()
}
