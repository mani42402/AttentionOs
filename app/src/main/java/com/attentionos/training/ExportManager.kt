package com.attentionos.training

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.attentionos.data.AttentionRepository
import java.io.File
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportManager(
    private val context: Context,
    private val repository: AttentionRepository,
) {
    suspend fun exportJsonLines(): ExportResult = withContext(Dispatchers.IO) {
        val examples = repository.exportableTraining()
        if (examples.isEmpty()) return@withContext ExportResult.Empty

        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(exportDirectory, "attentionos-training.jsonl")
        output.bufferedWriter().use { writer ->
            examples.forEach { example ->
                writer.append('{')
                writer.append("\"features\":").append(example.featuresJson).append(',')
                writer.append("\"label\":\"").append(example.expectedPriority).append("\",")
                writer.append("\"embedding_encoding\":\"int8_symmetric_127\",")
                writer.append("\"embedding_dimensions\":384,")
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
        repository.markExported(examples.map { it.id })
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            output,
        )
        ExportResult.Ready(uri, examples.size)
    }
}

sealed interface ExportResult {
    data object Empty : ExportResult
    data class Ready(val uri: Uri, val count: Int) : ExportResult
}
