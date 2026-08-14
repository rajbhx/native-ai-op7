package com.engine.nativeai

class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description =
        "Evaluate a safe arithmetic expression (+, -, *, /, ^, parentheses, decimals). Example: (2 + 3) * 4"

    override suspend fun execute(input: String): ToolOutput = try {
        val value = SafeExpr.evaluate(input)
        ToolOutput(name, format(value), true)
    } catch (e: Exception) {
        ToolOutput(name, "", false, e.message ?: "invalid expression")
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
