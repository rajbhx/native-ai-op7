# Next-phase plan — Tools & Engine Service (gated, architecture-preserving)

Status: ⬜ planned → ⬜ implemented → ✅ verified (each item: CI green →
on-device verified → field note). One logical change per revision. No new
library dependencies without a measured justification. Never fabricate
measurements; on-device numbers are labelled "contended" when the device is
in use.

## Goal
Close the two visible gaps against the spec without rewriting anything:
1. Tools are invisible and uncontrolled (terminal exists but is hardwired
   disabled; no permission gate in UI).
2. The engine service has no honest UI state (STOPPED/STARTING/READY/BUSY/
   ERROR), no stop/abort during generation, and graceful shutdown does not
   yet terminate managed exec processes.

Deliberately NOT in this phase: Vulkan/NNAPI enablement (Phase B below),
learning dataset pipeline (Phase C below), embedding/RAG vector memory.

## Phase A — Tools panel + service state (next)

### A1. Tool inventory contract
- Extend `AgentTool` metadata or add a `ToolDescriptor` (id, name, description,
  risk level, permission, availability, enabled, timeout) without changing the
  existing `AgentTool.execute` contract.
- Add `ToolRegistry.snapshot(): List<ToolDescriptor>` — single source of truth
  for UI; `descriptions()` keeps filtering by `available`.

### A2. Tools UI
- Add a `Tools` section to the engine settings bottom sheet (no new screen):
  list each registered tool with AVAILABLE / DISABLED / REQUIRES_APPROVAL
  badge; unknown stays UNKNOWN; no fake capabilities.
- Terminal row: enable toggle + command allowlist editor (persisted in
  `SharedPreferences`; default deny-all `ExecutionPolicy`).
- Wire the toggle into `runAgent` (EngineScreen): `TerminalTool.setEnabled` +
  policy allowlist applied before agent start.

### A3. Stop / abort
- Add a Stop control on the main screen (secondary, next to Clear) that
  cancels the active `runJob` and calls `engine.cancel()`; trace ends with a
  STOPPED event. UI state returns to READY. Test on device with local model.

### A4. Service state machine + graceful shutdown
- Expose service lifecycle via a small `EngineServiceState` (STOPPED/STARTING/
  READY/BUSY/ERROR) derived from: service bound?, engine loaded?, job running?.
- `EngineForegroundService.onDestroy`: cancel scope, close engine, and call
  `ExecutionBackend.shutdown()` (add tracking of managed processes to
  `LocalProcessBackend` so no orphaned shells survive).
- START_STICKY restart: re-create engine; UI re-syncs on rebind; watchdog
  keeps the 1.5 GB RSS ceiling.

### A5. Acceptance (on-device)
- Terminal toggle on → `terminal` appears in agent tool prompt; allowlist
  `echo` runs, `rm` denied by policy.
- Toggle off → terminal hidden from prompt and executor (already filtered).
- Start service → notification "READY"; stop → STOPPED; force-kill → restart.
- Stop button aborts a local generation mid-run; trace shows STOPPED.
- Remote + local quick-send and Agent both keep working unchanged.

## Phase B — Phase 7 sweep → backend abstraction (after A)
- On-device sweep: threads {2,3,4,6} × context {512,1024,2048} and, if Vulkan
  is present, n_gpu_layers {0, n/2}; sustained tok/s is the primary metric.
- Decide GGML flags / `-march` only from measured data; one optimization per
  revision.
- Add `InferenceBackend` abstraction (CPU/Vulkan/NNAPI) with runtime
  detection; UI shows only backends actually present, default off.

## Phase C — Verified learning dataset pipeline (after B)
- Finish `SelfLearningPipeline` gating: verification → quality score →
  dedupe → JSONL dataset → eligibility check (memory/thermal/battery/storage)
  → preserve for external training; never auto-train silently.

## Resource budget (every item must justify)
- No new runtime deps; UI additions are Compose-only (negligible RAM).
- Terminal enable does not spawn processes at rest — only when the tool runs.
- Service state UI adds no native memory; RSS watchdog unchanged (1.5 GB).

## Test matrix (JVM + device)
- Terminal: policy deny/allow, timeout, cancellation, hidden-when-disabled.
- Service: start/stop/restart, no orphan processes after shutdown.
- Stop/abort: local + remote generation interrupted, state returns READY.
- Regression: remote error path emits Error (flow transparency), tests 1-3.

## Phase D — Local model library + observability (combined increment, in progress)

Status: implemented → CI green → on-device verified → field note (A40/A41).

### D1. Local model library (multi-model, select-then-Load)
- `LocalModelLibrary` scans `filesDir/models` (`*.gguf`, ignores `.tmp`),
  stable ids (`model.gguf` → `local-llama`, others → `local-<stem>`).
- `LocalModelImporter`: SAF copy with progress, StatFs +64 MB margin, GGUF
  magic guard, temp rename. Import → rescan → auto-select → "tap Load Model".
- One `LocalModelProvider` per entry sharing the single `NativeEngine`
  (`LocalModelProvider` accepts a descriptor override); deleted files drop
  their provider; empty library keeps the `local-llama` placeholder.
- `RemoteProviderBootstrap.ensurePersistedSelection` skips all `local-*` ids.
- Model picker: "＋ Pick GGUF from storage…" row in LOCAL + size/quant subtitle.

### D2. Observability (metrics + diagnostics + jank + CI/dev gates)
- `AgentEvent` gains `atMs`/`durationMs`; trace lines render `HH:mm:ss`.
- `RuntimeMetrics` (load, first-token, tok/s, tools, errors/retries,
  restarts, jank) + `DiagnosticsProvider`/`RuntimeDiagnostics` +
  `DiagnosticsDialog` (Stats button).
- `FrameJankMonitor`: Choreographer dropped-frame counter, no dependency.
- LeakCanary `debugImplementation` only; `.maestro/acceptance/` flows run on
  the physical device (arm64-only APK can't run on x86_64 CI emulators).

### D3. Acceptance (on-device)
- LOCAL rows list every GGUF; SAF import adds without deleting others;
  selection persists across force-stop; Load required before Agent; model
  card shows size/quant; delete works; empty library doesn't crash.
- Stats shows measured values after a local run; trace timestamps; jank
  counter increments; restart counter after force-stop; Maestro flows pass.

## Phase E — Source knowledge base + runtime layer (implemented, CI green)

Status: implemented → CI green → partial on-device verify → field notes A46–A50.

### E1. Source KB completion (S1–S5)
- S4 seed catalog (`assets/sources.json`): Termux, llama.cpp, LiteRT, OP7
  playbook, uBlock Origin, MemPalace (uBO update-after-hours model).
- S2 GitHub rate-limit visibility: 403/429 with remaining=0 → clear error.
- S1 DOCUMENT ingestion: Termux `pdftotext` extraction, staged in app
  external files; metadata-only + explicit error when extractor unavailable.
- S3 startup auto-refresh: bounded, online-only, never blocks UI (shared
  Toolbox instance).
- S5 hybrid context: knowledge-seeking tasks (research/summary/document/
  debug/coding) inject top-3 source hits with `[source/file]` citations into
  the request context (ContextManager sources slot, compression order
  obs → memory → sources → user; system prompt untouched).

### E2. Runtime layer (S6–S7, real backends, gated)
- S6 MNN: `RuntimeKind` (LLAMA_GGUF/MNN/API/UNKNOWN) on `ModelDescriptor`
  (serialized); `InferenceBackend` seam; `MnnBackend` dlopen probe over a
  CI-bundled `libMNN.so` 3.6.1 (arm64-v8a, Apache-2.0, notice in
  `docs/third-party-notices.md`); model card runtime pill + runtime-aware
  local file line; Diagnostics shows MNN status.
- S7 USearch: vendored v2.11.3 headers + dedicated `vector-lib` + JNI
  `USearchVectorIndex` (bounded 10k, persisted, honest availability) +
  `EmbeddingProvider` seam; `SourceSearch` hybrid BM25+HNSW RRF over chunk
  ids is dormant until a real embedding provider exists.
- Diagnostics now also shows `USearchVectorIndex.selfTest()`.

### E3. Remaining (gated on on-device benchmark)
- Benchmark gate: measure MNN intent-classifier / embedder (RSS + tok/ms) on
  the OP7 before any MNN model or vector hybrid search is enabled. Until
  then no fake capabilities: BM25-only source search, GGUF-only local
  inference.
- On-device: verify Sources screen rows (seeded catalog + DOCUMENT add),
  startup refresh via logcat, model card runtime badge, Diagnostics MNN/
  VECT lines, hybrid context citations in a research-classified task.
