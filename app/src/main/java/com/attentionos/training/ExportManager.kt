package com.attentionos.training

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.attentionos.data.repository.AttentionRepository
import java.io.File
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportManager(
    private val context: Context,
    private val repository: AttentionRepository,
) {
    /**
     * Deletes any export left behind by a previous run.
     *
     * The exported file is a plaintext copy of the whole training corpus. It used to persist in
     * the cache indefinitely after sharing, so the app kept a second, unencrypted copy of the
     * data long after the user was done with it.
     */
    suspend fun discardStaleExports() = withContext(Dispatchers.IO) {
        File(context.cacheDir, EXPORT_DIRECTORY).listFiles()?.forEach { it.delete() }
        Unit
    }

    suspend fun exportJsonLines(): ExportResult = withContext(Dispatchers.IO) {
        val examples = repository.exportableTraining()
        if (examples.isEmpty()) return@withContext ExportResult.Empty

        val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        exportDirectory.listFiles()?.forEach { it.delete() }
        val output = File(exportDirectory, EXPORT_FILENAME)
        output.bufferedWriter().use { writer ->
            examples.forEach { example ->
                writer.append('{')
                writer.append("\"features\":").append(example.featuresJson).append(',')
                writer.append("\"label\":\"").append(example.expectedPriority).append("\",")
                writer.append("\"embedding_encoding\":\"int8_symmetric_127\",")
                writer.append("\"embedding_dimensions\":${EmbeddingCodec.EXPECTED_DIMENSIONS},")
                writer.append("\"embedding_q8_base64\":")
                if (example.embeddingQ8 == null) {
                    writer.append("null,")
                } else {
                    writer.append('"')
                        .append(Base64.encodeToString(example.embeddingQ8, Base64.NO_WRAP))
                        .append("\",")
                }
                writer.append("\"language_model\":")
                if (example.languageModelVersion == null) {
                    writer.append("null,")
                } else {
                    writer.append('"')
                        .append(example.languageModelVersion)
                        .append("\",")
                }
                writer.append("\"created_at\":").append(example.createdAt.toString())
                writer.append('}')
                writer.newLine()
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            output,
        )
        // Deliberately not marked exported yet: see confirmExported.
        ExportResult.Ready(uri, examples.size, examples.map { it.id })
    }

    /**
     * Records that a batch actually left the app.
     *
     * Rows used to be flagged the moment the file was written, before the share sheet even
     * opened, so dismissing the chooser consumed the batch permanently — those examples would
     * never appear in a later export. The caller now confirms only once the share has started.
     */
    suspend fun confirmExported(ids: List<Long>) = withContext(Dispatchers.IO) {
        repository.markExported(ids)
    }

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val EXPORT_FILENAME = "attentionos-training.jsonl"
    }
}

sealed interface ExportResult {
    data object Empty : ExportResult
    data class Ready(val uri: Uri, val count: Int, val exampleIds: List<Long>) : ExportResult
}
