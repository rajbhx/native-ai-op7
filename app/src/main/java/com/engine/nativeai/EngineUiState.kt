package com.engine.nativeai

/** Engine console state shown in the header, driven by real operations. */
enum class EngineUiState(val label: String) {
    READY("READY"),
    LOADING("LOADING MODEL"),
    THINKING("THINKING"),
    TOOL("EXECUTING TOOL"),
    VERIFYING("VERIFYING"),
    COMPLETED("COMPLETED"),
    ERROR("ERROR"),
    OFFLINE("OFFLINE"),
}
