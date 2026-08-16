# ROADMAP — Native Agentic AI Engine (native-ai-op7)

Status: ✅ shipped · 🟡 partial · ⬜ planned.
North star: memory-first, model-independent, self-learning agent — see
docs/VISION-MEMORY-FIRST.md for the requirement-by-requirement map.
Gate for every item: CI green → on-device verified → field note. A feature
is done only when code compiles, tests pass, and behavior is measured.
One measured optimization per revision; never fabricate capabilities.

## Phase 0 — Foundations ✅
- GitHub Actions: arm64-v8a debug APK, JVM tests, badging/ABI gates.
- llama.cpp pinned submodule (`b10428`); changes only via thin `patches/op7/`.
- Persistent debug keystore → installs update in place, no app-data wipe.

## Phase 1 — Native inference core ✅
- C++17 JNI bridge: mmap GGUF, 4 Kryo Gold/Prime threads, n_ctx 2048, Q8_0 KV.
- On-device verified: RSS ~729 MB after load; ~1.96 tok/s (contended).
- In-app GGUF downloader, CPU-thread + context selectors.
- ✅ Range-resume downloads: cancelled/interrupted attempts keep the .tmp
  partial; next attempt appends via `Range: bytes=N-`; SHA-256 stays
  full-file (existing bytes replayed from disk); servers ignoring Range
  restart cleanly.

## Phase 2 — Fast local memory 🟡
- SQLite memory: FTS5 (Android-10 fallback), BM25 ranking, decay, sessions,
  experiences/facts, tool-result logging, conversations/messages (v3) ✅.
- Vector retrieval ⬜ — parked until a memory-embedder on-device benchmark
  wins (ADR-007); revisit with TFLite embedder only behind `MemoryProvider`.

## Phase 3 — Agent orchestration + tools ✅ core
- ReAct state machine with bounded loop; provider-neutral `ModelProvider`.
- Tools behind `ToolRegistry` + bounded `ToolExecutor` +
  `PermissionManager` (READ_ONLY..PRIVILEGED): memory_search, calculator,
  web_search (local fallback), file_search, system_info, model_info,
  final_answer, terminal (disabled by default).
- Router AUTO/FREE/LOCAL/OFFLINE; explicit selection wins; free-anonymous
  Zen tier works; catalog cached + providers re-hydrated on restart.
- Execution layer (Termux-inspired, clean-room): `ExecutionBackend`,
  `LocalProcessBackend`, `ExecutionPolicy` (allow-list, deny by default),
  `TerminalTool` — audit: docs/source-research/termux-execution-audit.md.
- Track E (implemented): `TermuxBackend` at the core execution layer
  (RunCommandService intent + file exchange), `TermuxStatus` with honest
  reasons, `ExecutionManager` (Termux when READY, else local), Tools
  settings row (terminal ON/OFF, allowlist editor, Test connection).
- Dynamic 1.5 GB memory: `MemoryPlanner` sizes context against live
  available RAM (15% margin, hard 1536 MB cap) before model load; rejects
  with an actionable message instead of crashing.
- ✅ System-prompt/persona editing (SYSTEM PROMPT field in ENGINE SETTINGS,
  persisted override); ✅ chat/message history (collapsible drawer, reuse);
  ⬜ live stop/abort UX confirmation on-device (Stop button + [STOPPED]
  trace done; end-to-end verify pending).
- ✅ Fallback transparency: when the router substitutes the preferred model,
  the trace logs `FALLBACK <reason>` (rate-limited / unavailable in mode)
  and quick-completion status shows the same reason — no silent switching.
- ✅ Prompt survives process death (`SavedStateHandle`; model selection was
  already persisted in prefs).

## Phase 10 — Skill engine 🟡
- ✅ Skills wired into the agent system prompt by task type (`skillFor`).
- ✅ SKILLS panel in ENGINE SETTINGS: SEEDED/CUSTOM badges, create/edit/
  delete for user skills (built-ins read-only; JSON-persisted; empty list
  re-seeds defaults).
- ⬜ Skill versioning / reuse scoring (later).

## Phase 4 — Verified learning dataset pipeline 🟡
- ✅ Verified-experience JSONL export + dedupe + min-pair gate
  (`SelfLearningPipeline`), surfaced in Diagnostics → Export dataset with
  honest LoRA-eligibility reasons (never silently trains).
- ⬜ On-device volume gate (100+ verified pairs) + quality scoring before
  any external-training handoff.

## Phase 5 — Resource-aware background service 🟡
- `EngineForegroundService` (specialUse) holds engine + memory ✅.
- ⬜ Service state machine UI (STOPPED/STARTING/READY/BUSY/ERROR), crash
  recovery, graceful shutdown that terminates managed exec processes.

## Phase 6 — Experimental adapter training ⬜
- ⬜ LoRA behind memory/thermal/battery/dataset-quality gates; refuse to
  train when unsafe; preserve dataset for external training (ADR-004/007).

## Phase 7 — Benchmarking + optimization ⬜ (current focus)
- On-device sweep: 2–6 threads × n_gpu_layers, sustained tok/s as primary
  metric; decide any GGML flag/`-march` changes only from measured data.
- ✅ `InferenceBackend` abstraction (S6): `RuntimeKind` on `ModelDescriptor`,
  `MnnBackend` dlopen probe over a CI-bundled libMNN.so 3.6.1 (real backend,
  honest availability). ⬜ Vulkan/NNAPI toggles still gated on the Phase-7
  benchmark sweep; MNN model inference ships only after the same gate.
- ✅ USearch HNSW vector index (S7): vendored headers, dedicated vector-lib,
  `USearchVectorIndex` (bounded/persisted) + `EmbeddingProvider` seam;
  hybrid BM25+HNSW source search stays dormant until a benchmark-validated
  embedder exists (no fake capabilities).
- Memory monitor vs measured 1B envelope (KV 12.75 MiB, compute 302 MiB,
  RSS ~729 MB — docs/source-research/op7-memory-audit.md).
- Observability increment ✅: RuntimeMetrics (load/first-token/tok-s/tools/
  errors/jank), DiagnosticsProvider + DiagnosticsDialog, FrameJankMonitor,
  structured trace timestamps, LeakCanary (debug-only), Maestro flows —
  reference: docs/source-research/perf-observability-reference.md.

## Phase 8 — Production hardening 🟡
- ⬜ Release signing + R8, crash/ANR surfacing.
- ✅ Structured diagnostics/export (trace events with timestamp/run ID) —
  trace line timestamps + DiagnosticsDialog shipped; export/roll-up later.
- ⬜ Regression suite + field-note loop (every problem → docs/field-notes,
  log.yml, playbook sync).

## Cross-cutting contracts
- Interfaces over concrete deps: ModelProvider ✅, Tool/ToolRegistry ✅,
  ExecutionBackend ✅, SearchProvider ✅; InferenceBackend, MemoryProvider,
  FileSystemProvider, DiagnosticsProvider ⬜.
- ✅ Robolectric test infra: Context-bound unit tests (ModelPreferences
  SharedPrefs) run on the JVM; test-only dependency, no APK impact.
- ✅ Golden source catalog: `docs/GOLDEN-SOURCE-CATALOG.md` extended with
  agent-tooling and build/CI sections + license/fit analysis.
- Each new feature must justify RAM, CPU, storage, startup, battery, APK
  size. Unknown metadata stays UNKNOWN. No permanent daemons/processes
  without justification.

## Immediate next (ordered)
1. CI verification of the golden wave (resume, fallback transparency,
   process-death survival, gated ingest indexing, skills panel, dataset
   export, Robolectric) — static checks done, build not yet dispatched.
2. On-device acceptance of the current build: settings sheet now scrolls
   (Download/Stats/Start service reachable), startup status reflects the
   real library, Stop mid-generation + immediate re-run no longer overlaps
   the native engine (SIGSEGV fix, field note A42).
3. Verify remote persistence across restart, intent `--es prompt` hook,
   remote Send routing; observe DiagnosticsDialog values (real
   measurements only).
4. Run user tests 1–3 (math routing, ReAct memory loop, 2048-ctx sustained
   generation) and record results + field note A44+.
5. Phase 7 sweep (threads/GPU layers) → Vulkan/NNAPI decision.
6. Tools panel + service-state UI; then learning dataset pipeline.

## Definition of done
UI works · runtime/model state correct · errors translated · memory within
budget · offline behavior preserved · architecture modular · no fake
capabilities · tests/build pass · measured on device.

## Phase 9 — Source knowledge base + runtime layer 🟡 (track C)
- ✅ S4 seed catalog (uBlock Origin, MemPalace, Termux, llama.cpp, LiteRT,
  playbook) — uBO update-after-hours model, idempotent first-run seeding.
- ✅ S2 GitHub rate-limit visibility (403/429 → clear error, honest state).
- ✅ S1 DOCUMENT ingestion via Termux `pdftotext`; explicit error when the
  extractor is unavailable (metadata-only, no fabricated capability).
- ✅ S3 startup auto-refresh: bounded, online-only, never blocks the UI.
- ✅ S5 hybrid agent context: top-3 source hits with [source/file] citations
  for knowledge-seeking tasks (ContextManager sources slot; compression
  order obs → memory → sources → user; system prompt untouched).
- ✅ S6 MNN runtime probe + S7 USearch vector index (see Phase 7).
- ✅ Ingest-time vector indexing wired: `SourceUpdater.indexChunksIfReady`
  embeds + adds chunks to the HNSW index the moment the embedding gate
  opens; dormant by design until then (`chunksForFile` on SourceStore).
- ⬜ On-device benchmark gate → MNN embedder/intent model + vector hybrid
  search + embedding-backed memory (Phase 2 vector retrieval reopens).
- ⬜ On-device UI verification: Sources rows, DOCUMENT add, startup refresh,
  model-card runtime badge, Diagnostics MNN/VECT lines, hybrid citations.
