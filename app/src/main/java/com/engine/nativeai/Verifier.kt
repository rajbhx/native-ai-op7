package com.engine.nativeai

/**
 * Verifier (master prompt §18): the model is not its own source of truth.
 * Tool claims are checked against the actual tool result and, where possible,
 * against stored memory. Never fabricates evidence.
 */
data class VerificationResult(
    val passed: Boolean,
    val evidence: String,
    val tool: String,
)

object Verifier {

    /** A tool call is verified when it returned ok and produced evidence. */
    fun verifyTool(output: String, ok: Boolean, tool: String = "unknown"): VerificationResult =
        VerificationResult(
            passed = ok && output.isNotBlank(),
            evidence = if (ok && output.isNotBlank()) output.take(200) else "no usable output",
            tool = tool,
        )

    /** Memory claim check: the stored context must actually contain the claim. */
    fun verifyMemoryClaim(context: String, claim: String, tool: String = "memory_search"): VerificationResult =
        VerificationResult(
            passed = claim.isNotBlank() && context.contains(claim.trim(), ignoreCase = true),
            evidence = if (claim.isBlank()) "empty claim" else "claim '${claim.take(80)}' " +
                (if (context.contains(claim.trim(), ignoreCase = true)) "found in memory" else "NOT found in memory"),
            tool = tool,
        )
}
