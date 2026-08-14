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
- On-device local model: Qwen 0.5B Q4_K_M (491 MB gguf), mmap; stats: model=462 MB, ctx=2048, KV=Q8_0/Q8_0, threads=4, gpu=0, RSS=729 MB — comfortably inside the 1.5 GB budget. Device contended (in use), MemAvailable ~1.7 GB.
- Model catalog discovery works live: 62 models from https://opencode.ai/zen/v1/models, incl. big-pickle, deepseek-v4-flash-free, mimo-v2.5-free, nemotron-3-ultra-free; user-spec ids ling-3.0-flash-free/north-mini-code-free absent at refresh time — proves the list must stay dynamic.
- CI artifact install route: python3 -m http.server in proot + shizuku curl to /data/local/tmp + pm install -t (SELinux blocks pm from reading /sdcard).
