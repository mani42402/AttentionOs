package com.attentionos.training

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingCodecTest {
    @Test
    fun `normalized embedding round trips with high cosine similarity`() {
        val source = FloatArray(EmbeddingCodec.EXPECTED_DIMENSIONS) { index ->
            ((index % 31) - 15) / 100f
        }.normalized()

        val encoded = EmbeddingCodec.encode(source)
        val decoded = EmbeddingCodec.decode(encoded)

        assertNotNull(encoded)
        assertEquals(EmbeddingCodec.EXPECTED_DIMENSIONS, encoded!!.size)
        assertNotNull(decoded)
        assertTrue(cosine(source, decoded!!) > 0.999f)
    }

    @Test
    fun `invalid dimensions fail closed`() {
        assertNull(EmbeddingCodec.encode(FloatArray(12)))
        assertNull(EmbeddingCodec.decode(ByteArray(12)))
    }

    private fun FloatArray.normalized(): FloatArray {
        val magnitude = sqrt(sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(size) { this[it] / magnitude }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftMagnitude = 0f
        var rightMagnitude = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftMagnitude += left[index] * left[index]
            rightMagnitude += right[index] * right[index]
        }
        return dot / (sqrt(leftMagnitude) * sqrt(rightMagnitude))
    }
}
