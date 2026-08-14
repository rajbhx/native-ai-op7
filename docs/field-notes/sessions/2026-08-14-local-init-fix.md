# Session digest — 2026-08-14 — On-device local init fix + perf instrumentation

## Problems solved
- **P** local llama.cpp init fails on-device: "no backends are loaded"
  cause: AGP defaults android:extractNativeLibs=false, so .so files stay packed in the APK and applicationInfo.nativeLibraryDir is empty; ggml_backend_load_all() only searched exec dir + cwd, finding no CPU backend variants
  solution: android:extractNativeLibs="true" in AndroidManifest + ggml_backend_load_all_from_path(nativeLibraryDir) before llama_backend_init; verified on-device: "Native Engine Initialized Successfully (n_ctx=2048, n_threads=4)", CPU backend dlopen OK
  section: A
  tags: [llama.cpp, ggml, android, jni, apk, native-libs]
- **P** no measurable perf data for the performance gate (tokens/sec, first-token latency)
  cause: native generate loop logged nothing
  solution: NativeEnginePerf logcat tag logs prompt_tokens, prompt_eval_ms, first_token_ms, generated_tokens, gen_ms, tokens_per_sec per generation
  section: A
  tags: [llama.cpp, performance, benchmark]
- **P** remote models unusable: provider always sent empty Bearer key
  cause: ProviderRegistry.setApiKey existed but no UI to enter a key
  solution: runtime-only API-key dialog in Model Hub (memory only, never persisted/logged); live provider re-registered on save so the running agent picks up the key
  section: A
  tags: [provider, api-key, privacy]

## Notes (optional)
- On-device local model: the 491 MB gguf is actually a Qwen 1B (24 layers, n_embd 896, n_ctx_train 32768), not 0.5B as the filename suggests. mmap; stats: model=462 MB, ctx=2048, KV=Q8_0/Q8_0, threads=4, gpu=0, RSS=729 MB — comfortably inside the 1.5 GB budget. Device contended (in use), MemAvailable ~2.8 GB, cores at max freq (2.42/2.84 GHz).
- Baseline benchmark (contended, threads=4, 64 tokens): prompt eval 1.2-2.4 s for 3-6 tokens, first_token ~4 ms after prompt eval, generation 1.78-1.82 tok/s. Slow relative to expectations -> thread sweep (2-6) added to UI, re-benchmark when device is free.
- Thread selector (2-6) added to Model Hub; Load model now close()+init so thread changes apply without app restart.
- Model catalog discovery works live: 62 models from https://opencode.ai/zen/v1/models, incl. big-pickle, deepseek-v4-flash-free, mimo-v2.5-free, nemotron-3-ultra-free; user-spec ids ling-3.0-flash-free/north-mini-code-free absent at refresh time — proves the list must stay dynamic.
- CI artifact install route: python3 -m http.server in proot + shizuku curl to /data/local/tmp + pm install -t (SELinux blocks pm from reading /sdcard).

## Follow-up (same day)
- **P** clicking "Agent" crashes the app: SQLiteException "no such module: fts5 (code 1)"
  cause: OP7/OxygenOS Android 10 platform SQLite ships without the FTS5 extension; CREATE VIRTUAL TABLE ... USING fts5 failed inside MemoryDatabase.onCreate, and startSession ran outside the agent's try/catch -> uncaught coroutine exception -> FATAL EXCEPTION
  solution: MemoryDatabase detects FTS5 availability (try/catch around FTS5 DDL + triggers) and falls back to bounded LIKE retrieval (term-hit ranking, newest-first ties); runAgent now wraps startSession/endSession so memory failures degrade gracefully instead of crashing
  section: A
  tags: [sqlite, fts5, memory, crash, oneplus, oxygenos]
