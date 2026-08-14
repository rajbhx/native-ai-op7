# native-ai-op7

Native self-learning agentic AI engine for Mobile/Edge — OnePlus 7 edition
(Snapdragon 855: Kryo 485 1+3+4, Adreno 640, 6 GB RAM, UFS 3.0).

Stack: Kotlin (Compose UI + Foreground Service) + C++17 (llama.cpp JNI bridge,
GGML CPU dispatch, optional Vulkan later) + SQLite3 (FTS5 memory) + CMake.
arm64-v8a only. **Builds run on GitHub Actions, never locally.**

## Status (phases done, CI green)
- **Phase 1** native core: `NativeEngine` (RAII), streaming generation
  (Flow<String>, sampler chain, stop sequences, cancellation), `MemoryMonitor`.
- **Blueprint Phase 2** hardware profile: CPU topology, 4 threads pinned to
  Gold/Prime cores (4-7), `/proc/self/statm` RSS watchdog, 1.5 GB ceiling.
- **Phase 2/3** memory: raw SQLite + FTS5 (experiences, facts, tool_results,
  memory_scores, sessions), hybrid retrieval `BM25 × exp(-0.05·ageDays)`.
- **Phase 3/4/5** agent: ReAct loop on the formal state machine
  (UNDERSTAND→PLAN→EXECUTE→OBSERVE→VERIFY→FINALIZE→STORE / REPLAN),
  tools with permissions (READ_ONLY/SAFE/… enforced), Verifier,
  provider-neutral routing (local llama.cpp + OpenAI-compatible remote,
  FREE_FIRST/LOCAL_FIRST/OFFLINE_ONLY, health monitor + fallback chain).
- **Phase 4 spec** LMK protection: `EngineForegroundService` (specialUse)
  holds engine + memory; RSS watchdog; learning eligibility gate.
- **Phase 6/8** skills + sessions + `SelfLearningPipeline` (verified JSONL
  export, LoRA eligibility — never silently trains).
- **Blueprint Phase 6** UI: Jetpack Compose OxygenOS "NEVER SETTLE" dashboard —
  Model Hub, segmented mode selector, Live Agent Trace, Horizon Light.

## Layout
- `app/src/main/cpp/` — JNI bridge, `NativeEngine`, `MemoryMonitor`,
  `hardware_detector` (CMake; llama.cpp submodule)
- `app/src/main/java/com/engine/nativeai/` — engine, agent, memory, providers,
  skills, service, UI (`ui/`)
- `app/src/test/java/com/engine/nativeai/` — JVM unit tests (CI runs them)
- `.github/workflows/build.yml` — assembleDebug + badging gate + unit tests
- `docs/GOLD-STANDARD-SPEC.md` — the full engineering spec
- `docs/SYSTEM-PARAMETERS.json` — math/hardware system parameters payload
- `docs/source-research/` — architecture audit + ADR-001..010
- `docs/field-notes/` — journal consumed by the playbook sync

## Pinned dependency
- `third_party/llama.cpp` — submodule pinned to release **b10428** (`885c5bb`),
  shallow. Native code is written ONLY against the API in that exact checkout
  (verified: no obsolete symbols). Do not bump silently.

## Rules (from the playbook)
- Baseline before optimization; measure on the real device; one measured
  optimization per revision; revert on regression.
- Never publish an unvalidated build; free infra only
  (GitHub Actions/Releases/caches + Hugging Face for GGUF/LoRA artifacts).
- Phase status tracked in the playbook: `projects/native-ai-op7/roadmap.md`.
