# Session digest — 2026-08-14 — Phase 1 native core + Phase 2 memory

## Problems solved
- **P** lifecycleScope unresolved in MainActivity ("LifecycleOwner.lifecycleScope receiver mismatch")
  cause: lifecycleScope is a LifecycleOwner extension; plain Activity is not a LifecycleOwner
  solution: own CoroutineScope(SupervisorJob() + Dispatchers.Main), cancel in onDestroy, drop lifecycle-runtime-ktx
  section: A
  tags: [kotlin, android, lifecycle]
- **P** llama_load_model_from_file / llama_free_model deprecated at b10428
  cause: llama.cpp renamed the model API (llama_model_load_from_file / llama_model_free)
  solution: use the current names, verified against the pinned header
  section: A
  tags: [llama.cpp, api]
- **P** sampler chain params at b10428 carry only no_perf — penalties missing
  cause: llama_sampler_chain_params has just no_perf; penalties are a separate sampler object
  solution: llama_sampler_chain_add(chain, llama_sampler_init_penalties(n_vocab, last_n, repeat, 0, 0)) before top_k/top_p/temp/dist
  section: A
  tags: [llama.cpp, sampling]

## Notes (optional)
- Phase 1 done: NativeEngine.hpp/.cpp RAII split, MemoryMonitor, streaming
  generation via JNI callback + Kotlin Flow (callbackFlow), stop sequences,
  cancellation. CI green (badging gate passes).
- Phase 2 started: MemoryDatabase.kt — raw SQLite + FTS5 (experiences,
  semantic_facts, tool_results, memory_scores, sessions), external-content FTS
  with triggers, hybrid retrieval (BM25 -> utility/recency -> top-3), async
  maintenance (decay, low-utility delete, stale facts, VACUUM).
