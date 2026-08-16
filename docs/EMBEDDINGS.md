# On-device embeddings (hybrid search gate)

Source: **alibaba/MNN** (Apache-2.0) for the embedder runtime + **USearch**
(Apache-2.0, already vendored) for the HNSW index. Pipeline is wired in
`Toolbox.kt`; it stays dormant until every gate below is real — SourceSearch
never fabricates a vector capability.

## Current state (honest)
- `Toolbox.vectorIndex` = `USearchVectorIndex(dimensions = 384, ...)` — probes
  the bundled vector-lib; reports `available` only when the native index loads.
- `Toolbox.embeddingProvider` = `MnnEmbeddingProvider(...)` — `available` only
  when ALL of:
  1. `libMNN.so` loads (existing `MnnBackend` dlopen probe),
  2. `models/embeddings/embedding.mnn` exists (user-placed asset),
  3. `MnnEmbeddingProvider.EMBEDDING_GATE_PASSED == true`.
- `SourceSearch.search()` already runs reciprocal-rank fusion (BM25 + HNSW)
  when both report available; otherwise pure BM25 (see `VectorHybridTest`).

## Unblocking the gate (on-device, measured)
1. **Pick a model** — MiniLM-L6-v2 (384 dims) or bge-small-en-v1.5 (384 dims):
   small (≤ 100 MB fp32, ~30 MB quantized), good on-device quality, matches
   the `384` dimension constant in `Toolbox`.
2. **Convert** — ONNX export (`sentence-transformers` / `transformers` +
   `torch.onnx.export`), then `MNN-Convert --framework ONNX --modelFile
   model.onnx --MNNModel embedding.mnn --bizCode biz` (MNN tools, Linux x86).
3. **Place** — `adb push embedding.mnn /sdcard/Android/data/com.engine.nativeai/files/models/embeddings/embedding.mnn`
   (or import through the app once a UI path exists).
4. **Benchmark on the OP7** — first-token ms, tokens/ms, RSS delta, index
   build time on the source corpus. Record the numbers in
   `docs/field-notes/` — never claim quality without measurement.
5. **Flip the gate** — set `EMBEDDING_GATE_PASSED = true` in
   `MnnEmbeddingProvider.kt` only after step 4 is recorded.

## Then
- Implement `MnnEmbeddingProvider.embed()`: load `Interpreter` via the
  dlopen'd libMNN, run the forward pass, return the pooled CLS vector (or a
  `mean` pool). Currently returns `null` by design — no fake vectors.
- Wire chunk-time embedding writes into `SourceChunker`/`SourceUpdater` so
  the index is populated at ingest, and `save()` the index on agent runs.

## Why not a hash/keyword "embedding"
A lexical bag-of-words vector is not a semantic embedding; exposing it as one
would fabricate a capability and pollute the RRF merge with misleading
"vector" hits. BM25 already covers lexical search honestly.
