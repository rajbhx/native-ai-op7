# Session digest — 2026-08-16 — Memory ceiling + GGUF metadata + UX color roles

## Problems solved
- **P** native `rss_over_limit` flagged at 1500 MiB while the Kotlin
  watchdog/UI reported 1536 MiB (1.5 GB) — two different ceilings.
  cause: `MemoryMonitor.cpp` hardcoded `1572864000ULL` independently of
  `Op7SystemProfile.MEMORY_LIMIT_BYTES` (`1610612736`).
  solution: one `constexpr kOp7MemoryLimitBytes = 1610612736ULL` in
  `MemoryMonitor.hpp`, used by `statsJson`; contract test pins the value
  (`SYSTEM-PARAMETERS.json max_total_runtime: 1536` is authoritative).
  section: A
  tags: [memory, ceiling, cpp, contract-test]

- **P** KV-cache estimate used 28 layers / 2048 hidden for every model,
  overestimating the shipped Qwen2.5-1B GGUF (24 / 1024) ~2.3x and shrinking
  `maxSafeNctx`/headroom.
  cause: `MemoryPlanner`/`MemoryBudget` defaulted to 3B-class geometry, and
  pre-flight planning runs before the model loads, so real geometry was
  never available.
  solution: strict `GgufMetadataReader` (GGUF v2/v3 header: architecture /
  block_count / embedding_length; null on any anomaly — never fabricates),
  `GgufMetaCache` keyed by path/size/mtime, nullable `layers`/`hiddenDim`
  on planner + budget with labeled fallback constants, and EngineScreen
  pre-flight passes measured values (Qwen 1B @2048 ctx: ~96 MB KV, not 224).
  section: A
  tags: [gguf, metadata, memory-planner, qwen1b, kv-cache]

- **P** accent red leaked into status text, quick-download links, the
  download progress bar, and picker selection/links.
  cause: flat color tokens with no semantic roles.
  solution: semantic roles in `Theme.kt` (`OpPrimaryAction`,
  `OpStatusSuccess/Warn/Info/Danger`, `OpLinkAccent`) and remapped
  EngineScreen + ModelPicker so red = exactly one primary action per screen
  plus genuine errors; picker filter chips now match ValuePill's neutral
  outline/fill.
  section: A
  tags: [ux, color-tokens, red, hierarchy]
