# Session digest — 2026-08-16 — "Deliberately not done" increment (resume, fallback, survival, ingest)

Code-only pass answering what is actionable from the documented
"deliberately not done" list. Nothing built/pushed; static verification only
(CI is the compile gate). Items that stay gated are recorded with their gate.

## Implemented (code-only, honest)
- **Download resume (HTTP Range)**: `ModelDownloader` keeps `.tmp` on cancel
  / IOException; the next call sends `Range: bytes=N-` and appends. SHA-256
  is still full-file (existing bytes replayed from disk before appending), so
  the manifest checksum gate stays valid. Servers ignoring Range (200) or
  returning 416 restart cleanly. UI shows "paused — N MB cached; tap
  Download to resume" and a resume hint when a partial exists.
- **Fallback transparency**: `AgentEvent.Routed` gains `reason`; the router
  exposes `lastError(providerId)`; `ThinkingAgent` emits why a preferred
  model was substituted (rate-limited / unavailable in mode); the trace logs
  a `FALLBACK ...` line and quick-completion status shows the same reason.
  No silent switching.
- **Process-death survival**: `EngineViewModel` takes a `SavedStateHandle`
  (default-arg keeps existing tests) and persists the prompt; model
  selection already survives via prefs. Golden UX P0 item now covers
  process death, not just rotation.
- **Ingest vector indexing (gated)**: `SourceStore.chunksForFile(fileId)`
  (Android SQLite + JDBC twin + fake); `SourceUpdater.indexChunksIfReady`
  embeds and adds chunks at every ingest path only when the index AND a real
  embedder report available — dormant until the on-device embedding
  benchmark gate opens (docs/EMBEDDINGS.md). Failures never break ingest.

## Tests added (JVM)
- `EngineViewModelTest.promptSurvivesProcessDeathViaSavedState`
- `SourceStoreJdbcTest.chunksForFileReturnsChunksInIndexOrder`

## Deliberately still not done (unchanged, honest)
- `MnnEmbeddingProvider.embed()` real inference + gate flip — needs the
  validated `.mnn` asset and an on-device benchmark (EMBEDDINGS.md).
- Vulkan/NNAPI toggles — Phase-7 device sweep first.
- Skill create/edit UI (Phase 10) — skill wiring is in, management UI is not.
- Learning dataset pipeline (Phase C) — verification/eligibility gating.
- Room/DataStore and `security-crypto` — deliberate non-adoptions (raw
  SQLite + prefs; keys are memory-only, nothing at rest to encrypt).
- On-device acceptance of the whole wave + CI dispatch — blocked on the
  physical OP7 / user go-ahead to push.
