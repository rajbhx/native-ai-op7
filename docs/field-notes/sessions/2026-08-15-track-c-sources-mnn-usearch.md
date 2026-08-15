# Session digest — 2026-08-15 — Track C: source KB completion + MNN + USearch

## Problems solved
- **P** the S1 commit appended document tests AFTER the class's closing brace,
  so `now`/`hour`/`FakeFetcher` were unresolved outside `SourceUpdaterTest`.
  cause: hand-edited test file; the appended block landed after `}`.
  solution: moved the three document tests back inside the class; aligned the
  unchanged-document marker with the real 13-byte fake PDF (`doc-1-13`).
  Caught only by CI (`compileDebugUnitTestKotlin`), never by a local build.
  section: A
  tags: [testing, kotlin, ci, structure]
- **P** `SourceSeedLoaderTest.blankInputIsEmpty` failed with JSONException:
  `SourceSeedLoader.parse("")` throws while only `load()` caught exceptions.
  cause: org.json `JSONObject(String)` on blank input throws; parse() had no
  guard of its own.
  solution: `parse()` now catches and returns an empty list — malformed
  catalogs are honest state, never a crash.
  section: A
  tags: [org.json, parsing, robustness, testing]
- **P** one CI build per push forced serial validation (each push cancels the
  in-flight run via the `build-<ref>` concurrency group).
  cause: `on.push` builds only `main`, and every push to main cancels the
  previous run.
  solution: feature work moves to `ci/*` branches; `gh workflow run build.yml
  --ref ci/<branch>` starts a parallel build because the concurrency group is
  per-ref. main + s6 + s7 built simultaneously; merge the validated branch
  into main afterwards.
  section: A
  tags: [ci, parallel, github-actions, branches]
- **P** no runtime abstraction: local inference paths assumed GGUF/llama.cpp.
  cause: one engine from phase 1; MNN reference was parked.
  solution: `RuntimeKind` (LLAMA_GGUF/MNN/API/UNKNOWN) on `ModelDescriptor`
  (serialized), `InferenceBackend` seam, `MnnBackend` dlopen probe against a
  CI-bundled `libMNN.so` 3.6.1 (arm64-v8a). Honest availability — no MNN
  model inference until an on-device benchmark gate validates a real model.
  section: B
  tags: [mnn, runtime, abstraction, jni, apk-size]
- **P** no local vector index; roadmap promised "vector index" with no owner.
  cause: embeddings need a validated model; nothing real existed.
  solution: vendored USearch v2.11.3 headers (Apache-2.0, C++17, ARM disables
  fp16/simsimd paths) + dedicated `vector-lib` + JNI `USearchVectorIndex`
  (bounded 10k, persisted, honest availability) + `EmbeddingProvider` seam.
  `SourceSearch` hybrid BM25+HNSW RRF merge is dormant until an embedder
  passes the benchmark gate; stale vector keys are ignored.
  section: B
  tags: [usearch, vector, hnsw, hybrid, gating]
- **P** GitHub API license detection reports NOASSERTION for Matrix/btrace.
  cause: GitHub's detector can't classify the LICENSE text.
  solution: verified the actual files: Matrix LICENSE = BSD 3-Clause,
  btrace LICENSE = Apache-2.0 text with bundled third-party notices. Both
  concept-only here (our diagnostics are our own) — documented in
  `docs/source-research/source-license-review.md`.
  section: A
  tags: [licensing, observability, github-api]

## Decisions
- Source KB + hybrid context ship before MNN models: FTS5/BM25 stays v1;
  vectors engage only with a benchmark-validated embedding model.
- `libMNN.so` is downloaded at CI time (never committed) and its Apache-2.0
  notice lives in `docs/third-party-notices.md`.
