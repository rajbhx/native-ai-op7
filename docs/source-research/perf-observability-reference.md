# Source research — perf/memory/observability layer (2026-08-15)

Status: **reference + one staged increment** (RuntimeMetrics, DiagnosticsProvider,
FrameJankMonitor, LeakCanary debug-only, Maestro flows). Facts from upstream
repos at HEAD; licenses verified via GitHub API on 2026-08-15 unless noted.
Per source: problem / learn / do NOT copy / OP7 weight / verdict.

## Adopted in this increment (verified on-device during acceptance)

### LeakCanary (square/leakcanary) — Apache-2.0
- **Problem**: dev-time memory-leak detection that would otherwise crash later.
- **Learn**: heap-dump-on-leak via an installed RefWatcher; debug-only install.
- **Do NOT copy**: any runtime behavior into release builds.
- **OP7 weight**: `debugImplementation` only — absent from release; debug APK
  gains the dependency (dev sessions only).
- **Verdict**: adopt — `debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`;
  not a CI gate (heap analysis is manual/dev-time).

### BlockCanary (markzhai/AndroidPerformanceMonitor) — Apache-2.0
- **Problem**: UI-thread jank/ANR detection without a profiling suite.
- **Learn**: sample the main-thread loop / Choreographer frame deltas.
- **Do NOT copy**: the service + notification plumbing.
- **OP7 weight**: negligible — a `Choreographer.FrameCallback` counter.
- **Verdict**: adapt — `FrameJankMonitor` counts dropped/janky frames into
  `RuntimeMetrics`; no dependency added.

### Maestro (mobile-dev-inc/maestro) — Apache-2.0
- **Problem**: scriptable UI acceptance journeys ("Launch → picker → search →
  select → send → assert trace").
- **Learn**: YAML flows + assertions; physical-device execution.
- **Do NOT copy**: the CLI itself; flows are YAML-only in `.maestro/acceptance/`.
- **OP7 weight**: zero runtime cost; runs on a connected device via adb.
- **Verdict**: adopt flows for on-device acceptance. **Not in CI**: the APK is
  arm64-v8a only, so an x86_64 GitHub Actions emulator cannot install it;
  CI stays JVM tests + build, flows run against the physical OP7.

### Android GodEye (Kyson/AndroidGodEye) — Apache-2.0
- **Problem**: real-time CPU/RAM/network dashboard inside the app.
- **Learn**: one panel aggregating engine/process health without scattering UI.
- **Do NOT copy**: its plugin/module framework (heavyweight for this app).
- **OP7 weight**: UI-only; data already comes from `NativeEngine.memoryStats()`
  + `MemoryWatchdog` + `RuntimeMetrics`.
- **Verdict**: adapt concept — `DiagnosticsDialog` shows measured RSS, tok/s,
  tool timings, jank, service state from `DiagnosticsProvider` (no deps).

## Documented as reference only (parked behind triggers)

### USearch (unum-cloud/usearch) — Apache-2.0
- **Problem**: single-header C++11 HNSW vector search, small footprint.
- **Learn**: header-only HNSW; JNI-friendly memory layout; exact vs HNSW trade.
- **Do NOT copy**: until vectors are un-parked (ADR-007): adopt only if an
  on-device embedder benchmark wins within the 1.5 GB dynamic budget
  (embeddinggemma ~300 MB / minilm ~80 MB per MemPalace data) and FTS5+BM25
  proves insufficient. JNI vendoring of one header is low-cost when the
  trigger fires.
- **OP7 weight**: tiny (single header), but adds an embedding pipeline first.

### MNN (alibaba/MNN) — see docs/source-research/native-inference.md
- **Problem**: ultra-optimized mobile inference for Qualcomm.
- **Verdict**: keep as reference for a future standalone intent-matching
  classifier / embedding extractor; our LLM core stays llama.cpp (ADR-001).
  Same ADR-007 trigger as USearch: an embedder benchmark must win first.

### Tencent Matrix (Tencent/matrix) — license undetermined (GitHub API
NOASSERTION; verify LICENSE file before any reuse)
- **Problem**: production resource/battery/I-O monitoring.
- **Verdict**: reference only — heavyweight Java agent/plugin model conflicts
  with the no-new-runtime-deps rule; concepts (battery, I/O, startup) map to
  later `DiagnosticsProvider` sources, not to vendored code.

### Koala / btrace (bytedance/btrace) — license undetermined (GitHub API
NOASSERTION; verify LICENSE file before any reuse)
- **Problem**: startup time, FPS, thread-bottleneck tracing.
- **Verdict**: reference only — its bytecode-tracing approach needs a
  profiling stack we do not ship; concepts (startup/FPS tracking) belong in a
  later dev-tools build, not the runtime APK.

## Already covered elsewhere
- SQLite FTS5 (ADR-003, in use): keyword indexing/BM25 — no new action.
- uBlock (Track B): `SourceRegistry`/`SourceUpdater` conditional-fetch +
  eviction pattern already implemented.
- MemPalace: `docs/source-research/mempalace-reference.md` (scoped retrieval
  + pluggable store + hybrid search validate our Source KB shape).

## Integration policy (from the master prompt §27)
Every adoption here justifies RAM/CPU/storage/startup/battery/APK cost:
LeakCanary is debug-only, jank is a frame callback, metrics are in-memory
counters, Maestro is YAML on the host. Nothing ships in the release APK
without a measured reason.
