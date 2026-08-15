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
| LeakCanary 2.14 (debug only) | Apache-2.0 | debugImplementation; not in release APK. |
| USearch (reference only) | Apache-2.0 | Not vendored; parked behind ADR-007 trigger. |
| BlockCanary (concept only) | Apache-2.0 | No code vendored; FrameJankMonitor is our own. |
| Android GodEye (concept only) | Apache-2.0 | No code vendored; DiagnosticsDialog is our own. |
| Maestro (flows only) | Apache-2.0 | YAML on host/device; never bundled in the APK. |
| Tencent Matrix / btrace (reference only) | Undetermined — verify LICENSE file before any reuse | Not vendored. |

## Policy
- Reference projects (OpenCode, OpenClaw, Codex, Hermes, AgentScope,
  smolagents, OpenHands, LangGraph, Aider, Engram, MemOS, MNN) are studied,
  not vendored — no license obligations beyond this documentation.
- Never commit binaries, keys, or third-party source drops.
- Keep the dependency graph minimal (ADR-008).
