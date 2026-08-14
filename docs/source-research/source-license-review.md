# Source license review (native-ai-op7)

Everything embedded or consumed at build time:

| Component | License | Notes |
|---|---|---|
| llama.cpp (submodule, pinned b10428) | MIT | Embeddable; keep the copyright notice; no copyleft. |
| ggml (part of llama.cpp) | MIT | Same tree. |
| Gradle wrapper 8.9 | Apache-2.0 | Build tooling. |
| AGP / Kotlin / coroutines / core-ktx | Apache-2.0 | Android ecosystem, standard. |
| JUnit 4.13.2 | EPL-1.0 | Test-only. |
| org.json (test dep) | JSON license (MIT-style) | Test-only; runtime uses Android's org.json. |
| Vulkan headers / glslc (CI host) | Apache-2.0 / Khronos | Build-host only, not shipped. |

## Policy
- Reference projects (OpenCode, OpenClaw, Codex, Hermes, AgentScope,
  smolagents, OpenHands, LangGraph, Aider, Engram, MemOS, MNN) are studied,
  not vendored — no license obligations beyond this documentation.
- Never commit binaries, keys, or third-party source drops.
- Keep the dependency graph minimal (ADR-008).
