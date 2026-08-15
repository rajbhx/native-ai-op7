package com.engine.nativeai

data class Experience(
    val id: Long,
    val problemSummary: String,
    val approachSummary: String,
    val toolUsed: String,
    val resultSummary: String,
    val success: Boolean,
    val confidence: Float,
    val utility: Float,
    val timestamp: Long,
)

data class Fact(
    val id: Long,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val confidence: Float,
    val lastVerified: Long,
)

data class ToolResult(
    val id: Long,
    val toolName: String,
    val input: String,
    val outputSummary: String,
    val errorSummary: String?,
    val ok: Boolean,
    val timestamp: Long,
)

data class SessionInfo(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val meta: String,
)
