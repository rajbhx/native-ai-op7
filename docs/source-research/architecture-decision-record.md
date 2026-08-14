# Architecture Decision Record — native-ai-op7

Status: draft (updated 2026-08-14). Every decision maps to the gold-standard
spec (`docs/GOLD-STANDARD-SPEC.md`) and the playbook constraints (arm64-v8a
only, free infra only, GitHub Actions builds, 1.5 GB AI memory ceiling).

## ADR-001 — Native inference: llama.cpp as a pinned submodule, not a fork
- **Context**: GGUF/tokenizer/KV-cache/ARM kernels are proven and actively
  maintained. The OP7 build needs exact reproducibility.
- **Decision**: llama.cpp pinned at `b10428` as a git submodule; a thin
  `patches/op7/` layer may carry OP7-only tweaks; never fork wholesale.
- **Consequences**: every llama.cpp API change must be re-audited against the
  pinned header (`include/llama.h`). Already handled: KV-cache memory API
  (A6), model load/free rename (A8), sampler chain split (A9).
- **Not copied**: no llama.cpp server, no examples, no training code.

## ADR-002 — JNI surface stays minimal
- **Context**: Kotlin <-> C++ boundary is a stability and perf bottleneck.
- **Decision**: one `native-lib.cpp` glue + `NativeEngine.hpp/.cpp` +
  `MemoryMonitor`; JNI exports match the Kotlin externals exactly; streaming
  via a token callback + `callbackFlow`.
- **Consequences**: verified symbol-by-symbol against the pinned header before
  each commit (Phase 1 rule).

## ADR-003 — Fast memory is raw SQLite + FTS5, no Room/ORM
- **Context**: near-zero RAM overhead (~5-10 MB) and zero reflection cost.
- **Decision**: `MemoryDatabase.kt` with external-content FTS5 + triggers,
  hybrid retrieval (BM25 -> utility/recency -> Top-K=3), async maintenance
  (decay, low-utility pruning, stale-fact verification, VACUUM).
- **Consequences**: bind-arg typing is strict (`rawQuery` needs
  `String[]`, `execSQL` needs `Array<Any?>`); verified by CI + field notes A.

## ADR-004 — Provider-neutral model abstraction (multi-provider)
- **Context**: the engine must not depend on any single model backend
  (local llama.cpp, free remote, optional paid remote).
- **Decision**: `ModelProvider` interface + `ModelRegistry` dynamic catalog +
  `ModelRouter` (HYBRID/FREE_FIRST/LOCAL_FIRST/OFFLINE_ONLY) + health monitor
  + fallback chain (max 3 attempts). The agent kernel only sees the interface.
- **Consequences**: remote providers are OpenAI-compatible only
  (`/chat/completions`, SSE), zero new runtime dependencies (HttpURLConnection
  + org.json). API keys live in memory/runtime config only — never in source,
  logs, or the catalog file. Capabilities are declared, never guessed.

## ADR-005 — ReAct structured actions as the universal tool-call adapter
- **Context**: local models have no native tool-calling; remote models may or
  may not. One schema must work everywhere.
- **Decision**: the agent emits `{"action","input"}` JSON; `ActionParser`
  extracts strictly; `ToolExecutor` enforces timeout/limits/permission policy.
  Provider-native tool-call formats (future) convert into the same
  `AgentToolCall` shape.
- **Consequences**: no per-provider UI/agent branches; the trace is uniform.

## ADR-006 — Verification is explicit and tool-backed
- **Context**: the model must not be its own source of truth.
- **Decision**: every tool result carries `ok`/`error`; the agent emits
  `Verification(tool, passed)`; experiences are stored with `success` +
  `verified` flags and feed the learning pipeline only when verified.
- **Consequences**: no unverified experience is ever promoted to a training
  candidate (Phase 4 learning pipeline gate).

## ADR-007 — Privacy: local memory is the source of truth
- **Context**: remote models must never receive the whole database.
- **Decision**: only the minimal task context (summaries) is built per
  request; `MemoryPrivacyFilter` drops credential-like lines for remote
  requests; `PRIVATE_LOCAL` mode forbids the network entirely.
- **Consequences**: remote requests are opt-in per routing mode; secrets are
  excluded by construction.

## ADR-008 — Free infra only, builds on GitHub Actions
- **Context**: free-tier constraints from the playbook.
- **Decision**: all builds/tests/validation on GitHub Actions (assembleDebug +
  badging gate + JVM unit tests); APK artifacts on Releases; models/LoRAs on
  Hugging Face Hub; never commit binaries/keys.
- **Consequences**: on-device validation happens only when an OP7 is on adb;
  everything else is CI-gated.

## ADR-009 — Performance is measured, never assumed
- **Context**: "4 threads", "Vulkan faster", "N GPU layers" are hypotheses.
- **Decision**: benchmark 2/3/4/5/6 threads and GPU-layer configs on the real
  device (playbook Phase 2/7/8) before changing defaults; sustained
  performance is the primary metric; one measured optimization per revision.
- **Consequences**: `EngineConfig` defaults stay conservative (4 threads,
  gpuLayers=0) until evidence exists.

## ADR-010 — Self-learning is bounded and non-self-modifying
- **Context**: "self-learning" must not mean self-modifying executable code.
- **Decision**: pipeline = verified experience -> quality filter ->
  dedupe -> JSONL dataset -> eligibility check (memory/storage/thermal/
  battery) -> optional LoRA; if the envelope doesn't fit, DO NOT TRAIN —
  preserve the dataset for external training.
- **Consequences**: training is experimental and gated; never automatic.

## ADR-007 — LiteRT / LiteRT-LM parked as reference, not integrated
- **Context**: user research pass; LiteRT (ex-TFLite) and LiteRT-LM claim
  GPU/NPU LLM acceleration, tool use, and on-device LoRA. Our core is
  llama.cpp + GGUF (ADR-001), which LiteRT cannot run (`.tflite`/`.litertlm`
  only, no GGUF path).
- **Decision**: do not integrate now. Recorded as reference in
  `docs/source-research/litert-lm.md`. Revisit only on the trigger
  conditions there: (1) memory vector embeddings via a small TFLite embedder,
  (2) a benchmark on this exact OP7 beating llama.cpp within the 1.5 GB gate,
  (3) Phase 9 LoRA evaluation. Any integration must go behind the
  `ModelProvider` interface (ADR-004), never into the AgentKernel.
- **Consequences**: no new runtime dependency today; the llama.cpp core stays
  the single local backend until a measured win exists. Upstream moves fast
  (6-8 week cadence, LiteRT-LM v0.16.0 with C API prebuilts) — re-verify
  minSdk/Android 10 compatibility and model sizes before any future use.
