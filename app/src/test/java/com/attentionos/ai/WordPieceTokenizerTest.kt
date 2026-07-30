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
        val vocabFile = File("src/main/assets/models/minilm-vocab.txt")
        assertTrue("vocabulary asset missing at ${vocabFile.absolutePath}", vocabFile.exists())
        WordPieceTokenizer(vocabFile.readLines())
    }

    private data class Golden(val text: String, val ids: List<Long>)

    private fun golden(text: String, vararg ids: Long) = Golden(text, ids.toList())

    private val goldens: List<Golden> = listOf(
        golden("Need production fix ASAP", 101L, 2342L, 2537L, 8081L, 17306L, 2361L, 102L),
        golden("Your verification code is 448 291", 101L, 2115L, 22616L, 3642L, 2003L, 4008L, 2620L, 27173L, 102L),
        golden("hello", 101L, 7592L, 102L),
        golden("", 101L, 102L),
        golden("   ", 101L, 102L),
        golden("Café naïve résumé", 101L, 7668L, 15743L, 13746L, 102L),
        golden("Payment of \$42.50 was debited", 101L, 7909L, 1997L, 1002L, 4413L, 1012L, 2753L, 2001L, 2139L, 16313L, 2098L, 102L),
        golden("https://example.com/path?q=1", 101L, 16770L, 1024L, 1013L, 1013L, 2742L, 1012L, 4012L, 1013L, 4130L, 1029L, 1053L, 1027L, 1015L, 102L),
        golden("Meeting at 3:30pm — don't be late!", 101L, 3116L, 2012L, 1017L, 1024L, 2382L, 9737L, 1517L, 2123L, 1005L, 1056L, 2022L, 2397L, 999L, 102L),
        golden("你好世界", 101L, 100L, 100L, 1745L, 100L, 102L),
        golden("紧急：生产环境故障", 101L, 100L, 100L, 1993L, 1910L, 100L, 100L, 100L, 100L, 100L, 102L),
        golden("Hello 你好 world", 101L, 7592L, 100L, 100L, 2088L, 102L),
        golden("こんにちは", 101L, 1655L, 30217L, 30194L, 30188L, 30198L, 102L),
        golden("안녕하세요", 101L, 1463L, 30006L, 30021L, 29992L, 30010L, 30025L, 30005L, 30006L, 29997L, 30009L, 29999L, 30013L, 102L),
        golden("🚨 Emergency alert 🚨", 101L, 100L, 5057L, 9499L, 100L, 102L),
        golden("supercalifragilisticexpialidocious", 101L, 3565L, 9289L, 10128L, 29181L, 24411L, 4588L, 10288L, 19312L, 21273L, 10085L, 6313L, 102L),
        golden("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 101L, 100L, 102L),
        golden("UPPERCASE TEXT HERE", 101L, 3356L, 18382L, 3793L, 2182L, 102L),
        golden("multiple     spaces\tand\nnewlines", 101L, 3674L, 7258L, 1998L, 2047L, 12735L, 102L),
        golden("unaffordableword", 101L, 14477L, 4246L, 8551L, 3085L, 18351L, 102L),
    )

    @Test
    fun `matches reference tokenizer on golden vectors`() {
        val failures = goldens.mapNotNull { expected ->
            val actual = tokenizer.encode(expected.text, MAX_TOKENS).ids.toList()
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
        val ids = tokenizer.encode("你好世界", MAX_TOKENS).ids
        assertEquals("expected one token per ideograph plus [CLS]/[SEP]", 6, ids.size)
    }

    @Test
    fun `different CJK sentences no longer produce identical encodings`() {
        // This is the bug that mattered: every Chinese notification used to encode the same
        // way, so they all embedded identically and carried no information at all.
        val greeting = tokenizer.encode("你好世界", MAX_TOKENS).ids.toList()
        val incident = tokenizer.encode("紧急：生产环境故障", MAX_TOKENS).ids.toList()
        assertNotEquals(greeting, incident)
    }

    @Test
    fun `kana and hangul tokenize without collapsing to unknown`() {
        for (text in listOf("こんにちは", "안녕하세요")) {
            val ids = tokenizer.encode(text, MAX_TOKENS).ids
            assertTrue("\"$text\" should segment into several tokens", ids.size > 3)
            assertTrue(
                "\"$text\" should not be entirely unknown",
                ids.any { it != UNK_ID && it != CLS_ID && it != SEP_ID },
            )
        }
    }

    @Test
    fun `latin words survive alongside CJK in mixed text`() {
        val ids = tokenizer.encode("Hello 你好 world", MAX_TOKENS).ids.toList()
        assertTrue("\"hello\" should still be recognised", ids.contains(7592L))
        assertTrue("\"world\" should still be recognised", ids.contains(2088L))
    }

    @Test
    fun `encoding is truncated to the token budget`() {
        val long = List(200) { "notification" }.joinToString(" ")
        val encoded = tokenizer.encode(long, MAX_TOKENS)
        assertEquals(MAX_TOKENS, encoded.ids.size)
        assertEquals(MAX_TOKENS, encoded.mask.size)
        assertEquals("last token must remain [SEP]", SEP_ID, encoded.ids.last())
    }

    @Test
    fun `short input is not padded so the model runs a shorter sequence`() {
        val encoded = tokenizer.encode("hello", MAX_TOKENS)
        assertEquals("[CLS] hello [SEP]", 3, encoded.ids.size)
        assertTrue("every position must be attended", encoded.mask.all { it == 1L })
    }

    @Test
    fun `always brackets the sequence with CLS and SEP`() {
        for (text in listOf("", "hi", "a much longer notification body here")) {
            val ids = tokenizer.encode(text, MAX_TOKENS).ids
            assertEquals("CLS missing for \"$text\"", CLS_ID, ids.first())
            assertEquals("SEP missing for \"$text\"", SEP_ID, ids.last())
        }
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
        assertEquals(30_522, tokenizer.vocabularySize)
    }

    private companion object {
        const val MAX_TOKENS = 64
        const val CLS_ID = 101L
        const val SEP_ID = 102L
        const val UNK_ID = 100L
    }
}
