package com.attentionos.training

import kotlin.math.roundToInt

/**
 * Symmetric INT8 storage for normalized MiniLM vectors.
 *
 * MiniLM emits unit-normalized values in [-1, 1]. Quantizing to one signed byte per dimension
 * reduces each embedding from 1,536 bytes to 384 bytes without creating inference-time pressure.
 */
object EmbeddingCodec {
    const val EXPECTED_DIMENSIONS = 384

    fun encode(values: FloatArray?): ByteArray? {
        if (values == null || values.size != EXPECTED_DIMENSIONS) return null
        return ByteArray(values.size) { index ->
            (values[index].coerceIn(-1f, 1f) * SCALE)
                .roundToInt()
                .coerceIn(-127, 127)
                .toByte()
        }
    }

    fun decode(values: ByteArray?): FloatArray? {
        if (values == null || values.size != EXPECTED_DIMENSIONS) return null
        return FloatArray(values.size) { index -> values[index].toInt() / SCALE }
    }

    private const val SCALE = 127f
}
