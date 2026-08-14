package com.engine.nativeai

/**
 * Formal agent state machine (math/hardware spec §3):
 * IDLE -> UNDERSTAND -> PLAN -> EXECUTE -> OBSERVE -> VERIFY
 *   -> (valid) FINALIZE -> STORE
 *   -> (invalid) REPLAN -> PLAN
 * bounded by CANCELLED / iteration limits.
 */
enum class AgentState {
    IDLE,
    UNDERSTAND,
    PLAN,
    EXECUTE,
    OBSERVE,
    VERIFY,
    FINALIZE,
    STORE,
    REPLAN,
    CANCELLED,
}
