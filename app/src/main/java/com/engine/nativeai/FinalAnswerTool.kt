package com.engine.nativeai

class FinalAnswerTool : AgentTool {
    override val name = "final_answer"
    override val description =
        "Finish the task with the final answer. Input: the final answer text."

    override suspend fun execute(input: String): ToolResult =
        ToolResult(name, input.trim(), true)
}
