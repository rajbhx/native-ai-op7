package com.engine.nativeai

enum class AgentState {
    IDLE,
    THINKING,
    TOOL_CALL,
    OBSERVING,
    FINAL,
    CANCELLED,
}
