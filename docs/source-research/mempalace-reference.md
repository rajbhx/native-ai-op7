# MemPalace — Reference Note (future memory/Source-KB phases)

Status: **reference only, not integrated** (2026-08-15). Facts from upstream
`MemPalace/mempalace` @ main (MIT License — clean-room not required, but
keep the copyright notice if code is ever adapted). Local-first AI memory:
verbatim storage + pluggable retrieval backend, "96.6% R@5 raw on
LongMemEval, zero API calls".

## What it is
- Stores conversation history as **verbatim text** (no summarization /
  extraction / paraphrase); original content lives in *drawers*.
- Structured index: people/projects = *wings*, topics = *rooms*; searches are
  scoped to the structure instead of a flat corpus.
- Retrieval backend is pluggable (`mempalace/backends/base.py`:
  `BaseCollection` / `BaseBackend`, typed `QueryResult`/`GetResult`,
  uniform errors + `HealthStatus`). ChromaDB default; PostgreSQL/LanceDB/
  PalaceStore alternatives.
- Everything stays on-device unless the user opts in. Ships CLI + MCP server;
  embedding models download on first use (~80 MB minilm, ~300 MB
  embeddinggemma).

## Ideas that map onto the OP7 engine (Phase 2/3 + Source KB)
- **Scoped retrieval hierarchy** ≈ our `source_collections` → `sources` →
  `source_files` → `source_chunks`: scoped search (All / This collection /
  Current project) instead of one flat index. Already the Source KB shape;
  MemPalace validates it.
- **Pluggable store seam** ≈ our `SourceStore` / `MemoryProvider` contract —
  same pattern (typed results, backend errors, health status).
- **Hybrid search** (keyword text-match fallback when vector similarity misses
  exact terms, v4 #662) — matches our FTS5/BM25 + LIKE fallback; keep this
  hybrid when vectors are added later (ADR-007 revisit).
- **On-device embedder budget data**: minilm ~80 MB / embeddinggemma ~300 MB
  — concrete evidence a small embedder fits the 1.5 GB dynamic budget, so
  the deferred vector index has a realistic size estimate.
- **LongMemEval 96.6% R@5 raw** — a benchmark we can adopt to measure memory
  retrieval quality on-device instead of guessing.
- **Mtime-float precision fix (prevent re-mining unchanged files, #610)** —
  same class of bug as our local-file marker (`lastModified-length`);
  prefer strong markers (blob sha / revision) over mtime when available.

## Constraints to re-check before integration
- Python + ChromaDB stack is desktop-shaped; not portable to the OP7 runtime
  as-is. Use concepts, not the process model.
- Verbatim storage grows unboundedly — our `read_time` LRU eviction +
  bounded chunks stay mandatory for the 1.5 GB / storage budget.
