package com.attentionos.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the Kotlin static-embedding path against vectors produced by the reference
 * implementation (HuggingFace `tokenizers` + the same quantised table).
 *
 * A bake-off is only worth running if both encoders are actually the models they claim to be. A
 * tokenizer that differs from the reference by one normalisation rule would quietly hand the
 * comparison to the other candidate, and nothing in a scorecard would reveal it.
 */
@RunWith(AndroidJUnit4::class)
class StaticEmbeddingParityTest {

    @Test
    fun kotlinEncoderMatchesTheReferenceImplementation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vocabulary = context.assets
            .open(StaticEmbeddingAnalyzer.VOCAB_ASSET)
            .bufferedReader()
            .use { it.readLines() }
        assertEquals(
            "vocabulary size changed; the shipped table would no longer match",
            StaticEmbeddingAnalyzer.EXPECTED_VOCABULARY_SIZE,
            vocabulary.size,
        )
        val tokenizer = WordPieceTokenizer(vocabulary)

        val goldens = JSONArray(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(GOLDEN_ASSET)
                .bufferedReader()
                .use { it.readText() },
        )

        var compared = 0
        for (index in 0 until goldens.length()) {
            val case = goldens.getJSONObject(index)
            val text = case.getString("text")
            val expectedIds = case.getJSONArray("ids")
            if (expectedIds.length() == 0) continue

            val actualIds = tokenizer.encodePieces(text, StaticEmbeddingAnalyzer.MAX_TOKENS)
            assertEquals(
                "token count differs for \"$text\"",
                expectedIds.length(),
                actualIds.size,
            )
            for (position in 0 until expectedIds.length()) {
                assertEquals(
                    "token $position differs for \"$text\"",
                    expectedIds.getInt(position),
                    actualIds[position],
                )
            }
            compared++
        }
        assertTrue("no golden cases were compared", compared >= MINIMUM_CASES)
    }

    private companion object {
        const val GOLDEN_ASSET = "potion-golden.json"
        const val MINIMUM_CASES = 8
    }
}
