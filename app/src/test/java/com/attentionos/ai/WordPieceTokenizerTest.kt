package com.attentionos.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-vector tests for [WordPieceTokenizer].
 *
 * A tokenizer bug never surfaces as a crash: it silently yields a meaningless embedding and
 * every downstream decision quietly degrades. These vectors were produced by an independent
 * implementation of the BERT WordPiece algorithm (do_lower_case, strip_accents) run against
 * the very vocabulary this app ships, so agreement is a genuine cross-check rather than the
 * implementation being compared with itself.
 *
 * The tokenizer reads the real asset, so a swapped or corrupted vocabulary fails here first.
 */
class WordPieceTokenizerTest {

    private val tokenizer: WordPieceTokenizer by lazy {
        val vocabFile = File("src/main/assets/models/potion-vocab.txt")
        assertTrue("vocabulary asset missing at ${vocabFile.absolutePath}", vocabFile.exists())
        WordPieceTokenizer(vocabFile.readLines())
    }

    private data class Golden(val text: String, val ids: List<Int>)

    private fun golden(text: String, vararg ids: Int) = Golden(text, ids.toList())

    private val goldens: List<Golden> = listOf(
        golden("Need production fix ASAP", 1348, 1543, 7087, 16312, 1367),
        golden("Your verification code is 448 291", 1121, 21622, 2648, 1009, 3014, 1626, 26179),
        golden("hello", 6598),
        golden(""),
        golden("   "),
        golden("Café naïve résumé", 6674, 14749, 12752),
        golden("Payment of \$42.50 was debited", 6915, 1003, 8, 3419, 18, 1759, 1007, 1145, 15319, 1104),
        golden("https://example.com/path?q=1", 15776, 30, 19, 19, 1748, 18, 3018, 19, 3136, 35, 59, 33, 21),
        golden("Meeting at 3:30pm — don't be late!", 2122, 1018, 23, 30, 1388, 8743, 523, 1129, 11, 62, 1028, 1403, 5),
        golden("你好世界", 1, 1, 751, 1),
        golden("紧急：生产环境故障", 1, 1, 999, 916, 1, 1, 1, 1, 1),
        golden("Hello 你好 world", 6598, 1, 1, 1094),
        golden("こんにちは", 661, 29223, 29200, 29194, 29204),
        golden("안녕하세요", 469, 29012, 29027, 28998, 29016, 29031, 29011, 29012, 29003, 29015, 29005, 29019),
        golden("🚨 Emergency alert 🚨", 1, 4063, 8505, 1),
        golden("supercalifragilisticexpialidocious", 2571, 8295, 9134, 28187, 23417, 3594, 9294, 18318, 20279, 9091, 5319),
        golden("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1),
        golden("UPPERCASE TEXT HERE", 2362, 17388, 2799, 1188),
        golden("multiple     spaces\tand\nnewlines", 2680, 6264, 1004, 1053, 11741),
        golden("unaffordableword", 13483, 3252, 7557, 2091, 17357),
    )

    @Test
    fun `matches reference tokenizer on golden vectors`() {
        val failures = goldens.mapNotNull { expected ->
            val actual = tokenizer.encodePieces(expected.text, MAX_TOKENS).toList()
            if (actual == expected.ids) {
                null
            } else {
                "  \"${expected.text.take(40)}\"\n    expected ${expected.ids}\n    actual   $actual"
            }
        }
        assertTrue(
            "${failures.size} of ${goldens.size} golden vectors mismatched:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `CJK text segments per character rather than collapsing to one unknown`() {
        // Previously the word pattern swallowed a whole run of ideographs as a single "word",
        // WordPiece failed on it, and the sentence became [CLS] [UNK] [SEP].
        val ids = tokenizer.encodePieces("你好世界", MAX_TOKENS)
        assertEquals("expected one token per ideograph", 4, ids.size)
    }

    @Test
    fun `different CJK sentences no longer produce identical encodings`() {
        // This is the bug that mattered: every Chinese notification used to encode the same
        // way, so they all embedded identically and carried no information at all.
        val greeting = tokenizer.encodePieces("你好世界", MAX_TOKENS).toList()
        val incident = tokenizer.encodePieces("紧急：生产环境故障", MAX_TOKENS).toList()
        assertNotEquals(greeting, incident)
    }

    @Test
    fun `kana and hangul tokenize without collapsing to unknown`() {
        for (text in listOf("こんにちは", "안녕하세요")) {
            val ids = tokenizer.encodePieces(text, MAX_TOKENS)
            assertTrue("\"$text\" should segment into several tokens", ids.size > 3)
            assertTrue(
                "\"$text\" should not be entirely unknown",
                ids.any { it != UNK_ID && it != CLS_ID && it != SEP_ID },
            )
        }
    }

    @Test
    fun `latin words survive alongside CJK in mixed text`() {
        val ids = tokenizer.encodePieces("Hello 你好 world", MAX_TOKENS).toList()
        assertTrue("\"hello\" should still be recognised", ids.contains(6598))
        assertTrue("\"world\" should still be recognised", ids.contains(1094))
    }

    @Test
    fun `encoding is truncated to the token budget`() {
        val long = List(200) { "notification" }.joinToString(" ")
        assertEquals(MAX_TOKENS, tokenizer.encodePieces(long, MAX_TOKENS).size)
    }

    @Test
    fun `short input produces exactly its own tokens`() {
        assertEquals(1, tokenizer.encodePieces("hello", MAX_TOKENS).size)
    }

    @Test
    fun `rejects a vocabulary with duplicate entries`() {
        // Map building keeps the last duplicate, which would shift ids and corrupt embeddings.
        val error = assertThrows(IllegalArgumentException::class.java) {
            WordPieceTokenizer(listOf("[UNK]", "[CLS]", "[SEP]", "hello", "hello"))
        }
        assertTrue(error.message!!.contains("duplicate"))
    }

    @Test
    fun `rejects a vocabulary missing required special tokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            WordPieceTokenizer(listOf("hello", "world"))
        }
    }

    @Test
    fun `reports the shipped vocabulary size`() {
        assertEquals(29_528, tokenizer.vocabularySize)
    }

    private companion object {
        const val MAX_TOKENS = 64
        const val CLS_ID = 101
        const val SEP_ID = 102
        const val UNK_ID = 100
    }
}
