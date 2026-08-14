# LiteRT / LiteRT-LM — Reference Note (parked)

Status: **reference only, not integrated** (decision 2026-08-15, see
`architecture-decision-record.md` ADR-007). Facts verified from the upstream
repos on 2026-08-15 (`google-ai-edge/litert`, `google-ai-edge/LiteRT-LM`,
LiteRT-LM v0.16.0). Re-check before any integration — LiteRT moves on a
6-8 week cadence.

## What it is
- LiteRT is Google's rebranded TensorFlow Lite: on-device runtime for ML +
  GenAI. V2 adds a Compiled Model API (auto accelerator selection, async
  execution), CPU via XNNPack, GPU via ML Drift, plus NPU support.
- LiteRT-LM is the LLM orchestration layer on top of LiteRT: Android
  (Kotlin, stable), C++ (stable), Python (stable), JS/Swift/Flutter
  (preview/community). Cross-platform incl. Android/iOS/web/desktop/IoT.
- Model formats: `.tflite` and `.litertlm` (exported from PyTorch / TF / JAX
  / HF safetensors). **No GGUF path.** This is the hard incompatibility with
  our llama.cpp core.

## What it offers that could matter for us
- GPU/NPU acceleration for chat models (Adreno 640 is the OP7 GPU).
- Tool use / function calling APIs for agentic flows (maps to ADR-005
  `AgentToolCall` if ever adopted).
- Multi-token prediction (MTP) / speculative decoding ("up to 3x faster",
  Gemma 4 family) — the 1B Q4 local model sits at ~1.8 tok/s on this device.
- On-device LoRA support (MediaPipe LLM Inference lineage) — relevant to the
  Phase 9 experimental LoRA phase.
- Small embedding models (MobileBERT/USE-Lite, ~10-30 MB) — relevant to the
  memory engine's "optional vector retrieval" (Phase 3/6).
- Google AI Edge Gallery (Play Store) runs Gemma 3N E2B / Qwen2 on-device:
  a zero-code way to benchmark LiteRT-LM on this exact Snapdragon 855 before
  committing any code.

## Constraints to re-check before use
- `.litertlm` int4 2B models are ~1.2-1.5 GB — tight against the 1.5 GB AI
  runtime ceiling (local GGUF 1B Q4 already uses ~730 MB RSS).
- APK growth: second native runtime + AAR; conflicts with the "minimal
  dependency graph" rule unless justified by a measured win.
- Android 10 / API 29 compatibility of current LiteRT-LM releases: verify
  minSdk and device support before any dependency bump.
- OxygenOS/NNAPI Hexagon path on OP7 is OEM-dependent — never assume NPU.

## What NOT to do
- Do not replace the llama.cpp/GGUF core (ADR-001) on speculation. Conversion
  means a new export pipeline (HF safetensors -> .litertlm), a second runtime,
  and re-validation of the 1.5 GB memory gate.
- Do not let LiteRT touch the AgentKernel; any future integration goes behind
  the `ModelProvider` interface (ADR-004) as `LiteRTProvider`.

## When to revisit (trigger conditions)
1. Memory vector retrieval phase: add a small TFLite embedder via LiteRT for
   embedding-based `memory_search` ranking (no llama.cpp changes).
2. Benchmarks: if Google AI Edge Gallery or a `LiteRTProvider` prototype on
   the OP7 beats llama.cpp on sustained tok/s + first-token latency within the
   1.5 GB gate, consider it as an alternative local provider.
3. Phase 9 LoRA: if on-device adapter training moves forward, evaluate
   LiteRT-LM's LoRA path against llama.cpp LoRA export before choosing.
