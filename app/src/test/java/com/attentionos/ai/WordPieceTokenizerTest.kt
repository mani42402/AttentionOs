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
        val vocabFile = File("src/main/assets/models/multilingual-128d-vocab.txt")
        assertTrue("vocabulary asset missing at ${vocabFile.absolutePath}", vocabFile.exists())
        WordPieceTokenizer(vocabFile.readLines())
    }

    private data class Golden(val text: String, val ids: List<Int>)

    private fun golden(text: String, vararg ids: Int) = Golden(text, ids.toList())

    private val goldens: List<Golden> = listOf(
        golden("Need production fix ASAP", 15415, 11961, 63944, 24687, 10373),
        golden("Your verification code is 448 291", 12787, 44194, 25100, 13121, 10127, 35113, 28693),
        golden("hello", 29155),
        golden(""),
        golden("   "),
        golden("Café naïve résumé", 18427, 43786, 10598, 21337),
        golden("Payment of \$42.50 was debited", 58696, 10108, 109, 11437, 119, 10531, 10140, 65601, 10390),
        golden("https://example.com/path?q=1", 14540, 131, 120, 120, 14577, 119, 10241, 120, 26584, 136, 159, 134, 122),
        golden("Meeting at 3:30pm — don't be late!", 17829, 10160, 124, 131, 10225, 46445, 100, 11530, 112, 162, 10346, 12635, 106),
        golden("你好世界", 1856, 3019, 1666, 5855),
        golden("紧急：生产环境故障", 6572, 3712, 10046, 5830, 1751, 5689, 2908, 4251, 9181),
        golden("Hello 你好 world", 29155, 1856, 3019, 10228),
        golden("こんにちは", 1521, 18378, 11488, 29644, 11572),
        golden("안녕하세요", 1174, 26646, 49345, 13045, 35132, 25169, 47024),
        golden("🚨 Emergency alert 🚨", 100, 28348, 72456, 100),
        golden("supercalifragilisticexpialidocious", 12278, 17513, 14808, 26415, 33452, 35632, 10661, 19781, 80343, 47838),
        golden("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 100),
        golden("UPPERCASE TEXT HERE", 15961, 66840, 14059, 14048),
        golden("multiple     spaces\tand\nnewlines", 18248, 45714, 10110, 10246, 47096),
        golden("unaffordableword", 10155, 82796, 22741, 11522, 60961),
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
        assertTrue("\"hello\" should still be recognised", ids.contains(29155))
        assertTrue("\"world\" should still be recognised", ids.contains(10228))
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
        assertEquals(105_879, tokenizer.vocabularySize)
    }

    private companion object {
        const val MAX_TOKENS = 64
        const val CLS_ID = 101
        const val SEP_ID = 102
        const val UNK_ID = 100
    }
}
