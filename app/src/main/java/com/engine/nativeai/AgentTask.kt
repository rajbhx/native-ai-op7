package com.engine.nativeai

/** Input to the ModelRouter (spec §6). */
data class AgentTask(
    val prompt: String,
    val taskType: TaskType = TaskType.CHAT,
    val requiredCapabilities: Set<ModelCapability> = emptySet(),
    val contextLength: Int = 2048,
    val networkAvailable: Boolean = true,
    val allowPaid: Boolean = false,
    val preferLocal: Boolean = false,
)
