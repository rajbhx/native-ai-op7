# native-ai-op7

Native self-learning agentic AI engine for Mobile/Edge — OnePlus 7 edition
(Snapdragon 855: Kryo 485 1+3+4, Adreno 640, 6-8 GB RAM, UFS 3.0).

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
- **Phase 9** source knowledge base: uBO-style seed catalog (FMHY +
  playbook only), SITE/GitHub/WEB/DOCUMENT/RAW_TEXT/LOCAL ingestion,
  FTS5 chunks + hybrid agent context, `SourcesScreen` + `source_search`
  tool, startup auto-refresh, honest rate-limit errors.
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

## Golden-standard wiring (current)
- **Single authority**: `EngineViewModel` owns run state (StateFlows);
  rotation survives runs; agent/generate loops run off Main
  (`Dispatchers.Default`), UI updates are thread-safe StateFlow writes.
  The prompt also survives process death via `SavedStateHandle` (selection
  already persisted in prefs).
- **Multi-turn context**: the last session's tail is injected as
  `priorConversation` into every agent run (ContextManager priority:
  system > user > observations > conversation > memory > sources).
- **Citations**: `AgentEvent.Final` carries the source hits used; the trace
  renders `SOURCE · [title/path]` lines under the answer.
- **Router health**: one shared `ProviderHealthMonitor` (VM-owned) tracks
  failures + measured latency; routers sort by health, reliability, speed,
  then latency; failures are reported per run.
- **Fallback transparency**: when a preferred model is substituted, the
  trace (`FALLBACK ...` line) and status state the honest reason
  (rate-limited / unavailable in mode) instead of silently switching.
- **Model integrity**: `ModelManifest` (models/.manifest.json) records
  SHA-256 + size + URL per GGUF; `ModelDownloader` computes SHA-256 while
  streaming; `ensureLocalLoaded` refuses to load a checksum mismatch;
  `LocalModelLibrary.storageUsedBytes()` drives the STORAGE line.
- **Download resume**: cancelled/interrupted downloads keep the `.tmp`
  partial; the next attempt sends `Range: bytes=N-` and appends. SHA-256 is
  still computed over the full file (existing bytes replayed from disk), so
  resumed files pass the checksum gate. Servers that ignore Range restart
  cleanly.
- **Skills wired**: `Toolbox.skillManager` (seeded `DefaultSkills`) feeds
  the agent; the active skill's workflow/constraints append to the system
  prompt by task type.
- **Skills panel (Phase 10)**: ENGINE SETTINGS → SKILLS lists seeded vs
  custom skills; users create/edit/delete their own (built-ins read-only,
  persisted as JSON under app storage; empty list re-seeds defaults).
- **Memory lifecycle**: low-utility memories are pruned after each completed
  run (`deleteLowUtilityMemories`), best-effort.
- **Approval policy UI**: REQUIRES_APPROVAL tools get an ALWAYS toggle in
  ENGINE SETTINGS → TOOL INVENTORY (persisted in `toolAlwaysAllow`).
- **Benchmark in Diagnostics**: run the measured prompt battery
  (`ModelBenchmark`) per selected model, view results in the dialog.
- **Verified dataset export (Phase 4)**: Diagnostics → Export dataset runs
  `SelfLearningPipeline` (JSONL from verified experiences, dedupe, min-pair
  gate) and reports LoRA eligibility reasons — never silently trains.
- **Test infra**: Robolectric unit tests now cover Context-bound state
  (`ModelPreferences` SharedPrefs round-trips) on the JVM — test-only, no
  APK impact.
- **Embeddings**: hybrid BM25+HNSW is wired but gated — see
  `docs/EMBEDDINGS.md` (no fake vector capability). Chunks are indexed at
  ingest (`SourceUpdater.indexChunksIfReady`) the moment the embedding gate
  opens; until then the path is dormant by design.
- **Core error log**: every recorded failure lands in `ErrorLog` (ERRORS
  tab in the trace zone) — diagnostics probes fail closed, never crash.
- **GGUF metadata**: `GgufMetadataReader`/`GgufMetaCache` reads real GGUF
  header values (layers, hidden dim, context) for the model card and drives
  `MemoryBudget` from measured values instead of guesses.
- **Source knowledge base (Phase 9)**: seed catalog is FMHY + playbook only
  (external knowledge for retrieval; `SourceSearchTool` cites
  `[source/file]`). SITE ingestion (`SitemapParser` + `HtmlTextExtractor`)
  plus GitHub/WEB/DOCUMENT/RAW_TEXT/LOCAL types; `SourcesScreen` search +
  empty states + ADD dialogs for every type (FlowRow, no clipping);
  startup auto-refresh (`updateOnce`, bounded/online-only); GitHub
  403/429 → honest rate-limit errors; stale errors clear on successful
  refresh; hit titles stay neutral.
- Source catalog: `docs/GOLDEN-SOURCE-CATALOG.md`.

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
