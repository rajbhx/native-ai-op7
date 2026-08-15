# OP7 memory audit — measured (2026-08-15)

Measured on-device (OnePlus 7 GM1901, Android 10, 8 GB, contended) with the
shipped Qwen **1B** Q4_K_M GGUF (491,400,032 bytes / 468.6 MiB on disk,
24 layers, n_embd 896, GQA). Hard AI budget: 1536 MB.

## llama.cpp init log values (verified 2026-08-15)

| Item | Measured | Note |
| --- | --- | --- |
| KV cache (Q8_0 K+V) | **12.75 MiB** total | 2048 cells, 24 layers, 1 seq; K 6.38 MiB + V 6.38 MiB |
| CPU compute buffer | **302.00 MiB** | n_batch 512, worst-case graph reserve |
| Model weights | 468.6 MiB on disk | mmap-backed; resident share depends on touched pages |
| Process RSS after init | ~729 MB | contended measurement (phone in use) |
| Headroom vs 1536 MB | ~807 MB | RSS-based, dynamic |

## Why the naive KV formula overestimates

`2 * n_ctx * L * H * B_element` (2*2048*24*896 ≈ 84 MiB) assumes MHA with
full hidden dim. Qwen 1B uses GQA, and llama.cpp reports the real KV
allocation: **12.75 MiB** for 2048 ctx. Prefer llama's reported values
(`llama_kv_cache: size = ...`) over the formula; `MemoryBudget.estimate`
remains a conservative planning upper bound, not a measurement.

## Status

- 1B model fits the 1.5 GB envelope with large headroom; LMK risk low on
  6–8 GB RAM devices (RSS ~729 MB observed).
- Inference speed ~1.96 tok/s (64 tokens, 4 threads, contended) — bottleneck
  is memory bandwidth/thermal on Kryo 485, not SIMD (GGML_CPU_ALL_VARIANTS
  already dispatches armv8.2-a dotprod/fp16 kernels at runtime; no hardcoded
  `-march` per spec "detect, don't hardcode").
