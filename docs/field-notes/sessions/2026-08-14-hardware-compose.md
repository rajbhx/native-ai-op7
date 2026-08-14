# Session digest — 2026-08-14 — OP7 hardware profile + Compose UI

## Problems solved
- **P** ld.lld: undefined symbol ggml_threadpool_new / ggml_threadpool_free
  cause: threadpool API lives in the CPU backend (ggml-cpu.c), built as a
  dlopen'd shared lib under GGML_BACKEND_DL=ON — not linkable from native-lib
  solution: pin the calling thread (which drives llama_decode) before first
  graph compute; pthreads inherit the creator's affinity. No direct ggml-cpu
  link needed. Logcat line added ("Successfully pinned execution thread …").
  section: A
  tags: [llama.cpp, affinity, linker, ggml]
- **P** MainActivity setContent "receiver type mismatch"
  cause: androidx.activity.compose.setContent is a ComponentActivity extension;
  the activity extended plain android.app.Activity
  solution: extend androidx.activity.ComponentActivity
  section: A
  tags: [compose, kotlin, activity]

## Notes
- Blueprint Phase 2 (hardware profile): hardware_detector.{hpp,cpp} — CPU
  topology (possible cores), highCores() (top half → 4-7 on OP7), rssBytes()
  from /proc/self/statm, pinCurrentThread(); NativeEngine pins the decode
  thread when pin_high_cores=true; MemoryMonitor statsJson now carries
  rss_bytes / rss_limit_bytes / rss_over_limit; JNI nativeGetRssBytes;
  Kotlin MemoryWatchdog with 1.5 GB ceiling.
- Blueprint Phase 6 (UI): Jetpack Compose OxygenOS dashboard — Theme.kt tokens,
  EngineScreen (Model Hub cards, segmented mode pills, Agent Trace, Horizon
  Light pulse), MainActivity via setContent; XML layout + pill drawables
  removed. compose-bom 2024.06.00, material3, activity-compose, compose
  compiler 1.5.14 (Kotlin 1.9.24).
- Phase 6/8 spec: skills (Skill/Registry/Storage/Manager + JSON persistence +
  DefaultSkills seeds), sessions (start/end/recent in MemoryDatabase),
  SelfLearningPipeline (verified JSONL export + LoRA eligibility gate that
  never silently trains).
- Phase 5: ToolPermission (READ_ONLY/SAFE/REQUIRES_APPROVAL/PRIVILEGED)
  enforced in ToolExecutor; Verifier (tool success + memory-claim checks).
