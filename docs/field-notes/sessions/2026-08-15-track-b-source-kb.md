# Session digest — 2026-08-15 — Track B: uBO-style source knowledge base

## Problems solved
- **P** schema v3's `onUpgrade` was a dev reset: every update dropped the
  whole memory DB (experiences/facts/messages) and rebuilt it.
  cause: `onUpgrade` called DROP TABLE + `onCreate` (dev-only shortcut).
  solution: `SCHEMA_VERSION = 4` with a real migration — v3->v4 keeps every
  existing table and only adds the source KB tables (`source_collections`,
  `sources`, `source_files`, `source_chunks`, external FTS5
  `source_chunks_fts` + triggers). DDL lives in `SourceSchema` (pure strings)
  so the exact statements run on device and in JVM tests.
  section: A
  tags: [sqlite, migration, schema, sources]
- **P** source registry/updater logic could not be JVM-tested because
  `MemoryDatabase` extends Android `SQLiteOpenHelper`.
  cause: no storage contract; tests only had the Android implementation.
  solution: `SourceStore` interface implemented by `MemoryDatabase`
  (Android) and `JdbcSourceStore` (test-only `org.xerial:sqlite-jdbc`,
  which bundles FTS5) running the same `SourceSchema` DDL; `FakeSourceStore`
  for registry/updater unit tests. Tests cover migration retention, chunker
  bounds, candidate staleness order, 304 conditional fetch, GitHub
  changed-blob-only, read-time eviction, BM25 + LIKE search.
  section: A
  tags: [testing, sqlite-jdbc, jvm, sources]
- **P** RAW_TEXT sources would be marked ERROR on the next periodic refresh.
  cause: updater dispatched RAW_TEXT to the HTTP path, which needs a URL.
  solution: RAW_TEXT skipped in `updateSourceInternal` (indexed once on add).
  section: A
  tags: [sources, updater, bug]
- **P** chunk overlap disappeared after the word-boundary refactor.
  cause: the loop advanced `start = end` (no overlap).
  solution: advance `start = end - overlapChars` with a progress guard;
  overlap asserted in `SourceChunkerTest`.
  section: A
  tags: [chunker, retrieval]

## Architecture notes
- uBO-style (clean-room, GPL-3.0 ideas only): versioned seed catalog
  (`assets/sources.json`, `SourceSeedLoader`), per-source
  `updateAfterHours`, conditional fetch (ETag/Last-Modified/GitHub revision
  compare), changed-blob-only re-chunking, read-time LRU eviction (keep 20),
  serial gentle updater (most-obsolete first), 16 MB per-run byte budget,
  no background polling.
- Vectors stay FTS5/BM25-only for v1 (ADR-007); OP7/OxygenOS 10 SQLite
  without fts5 falls back to LIKE exactly like `experiences_fts` (A27).
- Agent tool `source_search` registered in the shared `Toolbox`; UI
  `SourcesScreen` reached from the main screen via the SOURCES chip.
