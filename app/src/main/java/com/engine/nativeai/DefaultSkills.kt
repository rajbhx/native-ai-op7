package com.engine.nativeai

/** Seed skills — editable local workflows, not executable code (spec §11). */
object DefaultSkills {

    fun seeds(): List<Skill> = listOf(
        Skill(
            id = "research",
            purpose = "Find up-to-date information on a topic before answering.",
            tools = listOf("web_search", "memory_search", "final_answer"),
            workflow = listOf(
                "classify the request",
                "search memory for prior context",
                "search the web for current facts",
                "answer with sources in the observation",
            ),
            constraints = "Never fabricate sources; if search is unavailable, say so.",
            verificationRules = listOf("web_search returned ok or explicitly unavailable"),
            builtin = true,
        ),
        Skill(
            id = "android",
            purpose = "Answer Android/Kotlin engineering questions with verified memory.",
            tools = listOf("memory_search", "calculator", "final_answer"),
            workflow = listOf(
                "recall relevant experiences from memory",
                "reason step by step",
                "give a concise final answer",
            ),
            constraints = "Local engine only for offline reasoning.",
            verificationRules = listOf("memory claims are checked against stored context"),
            builtin = true,
        ),
        Skill(
            id = "oneplus",
            purpose = "OnePlus 7 / Snapdragon 855 optimization guidance.",
            tools = listOf("system_info", "memory_search", "final_answer"),
            workflow = listOf(
                "check system_info for the live engine state",
                "search memory for prior benchmark lessons",
                "answer with measured constraints only",
            ),
            constraints = "Never claim a performance number without a measurement.",
            verificationRules = listOf("performance claims cite a measurement"),
            builtin = true,
        ),
        Skill(
            id = "coding",
            purpose = "Short Kotlin/C++ coding tasks with safe evaluation.",
            tools = listOf("calculator", "file_search", "final_answer"),
            workflow = listOf(
                "plan the implementation",
                "check app-private files if relevant",
                "produce the code in the final answer",
            ),
            constraints = "Output code as text; never execute model-generated code.",
            verificationRules = listOf("code is presented as text, not executed"),
            builtin = true,
        ),
    )
}
