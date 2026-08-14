package com.engine.nativeai

sealed class AgentEvent {
    data class Token(val text: String) : AgentEvent()
    data class ToolCall(val tool: String, val input: String) : AgentEvent()
    data class Observation(val tool: String, val output: String) : AgentEvent()
    data class Final(val answer: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}
