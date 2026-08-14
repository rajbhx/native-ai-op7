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

## MNN (Alibaba)
- **Problem**: lightweight cross-platform NN inference (mobile-first).
- **Learn**: mobile-first quantization + operator kernels; ARM NEON tuning.
- **Do NOT copy**: its model format/converters; our runtime is GGUF-based.
- **Android**: yes. **OP7 weight**: fine, but it does not serve GGUF/llama
  stacks as directly as llama.cpp.
- **Rewrite natively**: n/a — not adopted. No dependency introduced.

## Verdict (ADR-001/ADR-002)
Adopt llama.cpp pinned; minimal custom JNI; no other inference engine.
