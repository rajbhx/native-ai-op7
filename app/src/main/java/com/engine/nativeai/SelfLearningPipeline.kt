package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Phase 4 spec — verified self-learning pipeline (master prompt §19-20).
 *
 * Learning means: verified experience collection -> quality filter ->
 * deduplication -> JSONL dataset. It NEVER means self-modifying code.
 * On-device LoRA training is experimental and eligibility-gated; if the
 * resource envelope doesn't fit, the dataset is preserved for external
 * training instead of training locally.
 */
data class TrainingPair(val prompt: String, val completion: String)

data class ExportResult(
    val exported: Int,
    val file: File?,
    val reason: String,
)

data class LoRAEligibility(
    val eligible: Boolean,
    val reasons: List<String>,
)

class SelfLearningPipeline(
    private val memory: MemoryDatabase,
    private val trainingDir: File,
    val minPairs: Int = 100,
) {

    /** Export verified experiences as JSONL {"prompt","completion"} pairs. */
    suspend fun exportVerifiedTrainingData(): ExportResult = withContext(Dispatchers.IO) {
        val pairs = memory.verifiedExperiences(limit = 1000).mapNotNull { e ->
            val prompt = e.problemSummary.trim().take(500)
            val completion = e.resultSummary.trim().take(500)
            if (prompt.isBlank() || completion.isBlank()) null else TrainingPair(prompt, completion)
        }
        if (pairs.isEmpty()) {
            return@withContext ExportResult(0, null, "no verified experiences yet")
        }
        val deduped = pairs.distinctBy { it.prompt.lowercase() to it.completion.lowercase() }
        trainingDir.mkdirs()
        val file = File(trainingDir, "dataset-${System.currentTimeMillis()}.jsonl")
        file.writeText(
            deduped.joinToString("\n") { pair ->
                JSONObject()
                    .put("prompt", pair.prompt)
                    .put("completion", pair.completion)
                    .toString()
            },
        )
        ExportResult(deduped.size, file, "ok")
    }

    /**
     * Eligibility gate for experimental on-device LoRA. Returns a decision
     * and reasons; never silently trains. Default: not eligible.
     */
    fun triggerBackgroundFinetune(export: ExportResult): LoRAEligibility {
        val reasons = mutableListOf<String>()
        if (export.exported < minPairs) {
            reasons.add("need >= $minPairs verified pairs, have ${export.exported}")
        }
        if (export.file == null) {
            reasons.add("no dataset file to train on")
        }
        reasons.add("on-device LoRA training is experimental and disabled by default; " +
            "dataset preserved for external training")
        return LoRAEligibility(reasons.isEmpty(), reasons)
    }
}
