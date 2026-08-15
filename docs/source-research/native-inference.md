# Source research — native inference (llama.cpp, llama-kotlin-android, MNN)

Per the master prompt §27: for each source — problem solved, architecture to
learn, what NOT to copy, Android feasibility, OP7 weight, native rewrite
candidates, dependencies introduced.

## llama.cpp (ggml-org/llama.cpp)
- **Problem**: efficient local LLM inference (GGUF, KV cache, ARM/Vulkan
  backends).
- **Learn**: GGUF format + mmap loading; tokenizer; llama_decode pipeline;
  KV-cache memory API (`llama_get_memory` at b10428); sampler chain
  (penalties -> top_k/top_p/temp/dist); CPU feature dispatch
  (`GGML_CPU_ALL_VARIANTS` + `GGML_BACKEND_DL`); CMake option surface.
- **Do NOT copy**: the server (`llama-server`), examples, training code,
  model downloads. Only the library is consumed.
- **Android**: yes — official `build-android.yml` proves arm64 CI builds.
- **OP7 weight**: library only; model size dominates (Q4_K_M ~3-4 GB on disk,
  mmap keeps RSS bounded); Vulkan offload is optional and must be measured.
- **Rewrite natively**: no — pin as a submodule (ADR-001).
- **Dependencies**: none beyond what CMake pulls (ggml, Vulkan headers on the
  build host).

## llama-kotlin-android
- **Problem**: idiomatic Kotlin/JNI wrapper around llama.cpp for Android.
- **Learn**: coroutine integration, streaming, model lifecycle, JNI shape.
- **Do NOT copy**: wholesale (master prompt §2 — "do not blindly import");
  our JNI surface is intentionally minimal (ADR-002).
- **Android**: yes. **OP7 weight**: negligible (thin wrapper).
- **Rewrite natively**: we wrote our own thin bridge — equivalent result with
  fewer moving parts.

## MNN (Alibaba) — ADOPTED (S6, probe only)
- **Problem**: lightweight cross-platform NN inference (mobile-first).
- **Learn**: mobile-first quantization + operator kernels; ARM NEON tuning.
- **Adopted**: `libMNN.so` 3.6.1 (arm64-v8a) is bundled by CI and probed via a
  dlopen JNI shim (`MnnBackend`) so the runtime reports honest availability.
  No MNN model file or inference path ships until an on-device benchmark gate
  validates a real model (RSS/tok-ms) — the spec forbids fake acceleration.
- **Runtime**: `RuntimeKind.MNN` on `ModelDescriptor`; picker/card show the
  runtime badge. GGUF-only paths are removed from the local-model line.

## Verdict (ADR-001/ADR-002)
Adopt llama.cpp pinned; minimal custom JNI; MNN bundled as a probed runtime,
not a second inference path yet.

## PocketPal AI (llama.rn usage patterns)
- **Learn**: gguf download flow guards (verify magic before rename; free-space
  check via statfs; resume/cancel), llama.rn context params (n_ctx, n_threads,
  n_gpu_layers, KV type), per-device fallback rules, benchmark harness shape.
- **Do NOT copy**: the native RN bridge or its bundled llama.cpp build; we keep
  our pinned b10428 submodule + JNI + Vulkan-optional CMake.
- **Adapted**: ModelDownloader.kt mirrors the download guards; context/threads
  remain our native EngineConfig knobs; GPU layers stay 0 until a Vulkan
  backend is verified on the OP7 (never assume).
