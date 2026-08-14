# Session digest — 2026-08-15 — LiteRT/LiteRT-LM research (parked as reference)

## Problems solved
- **P** should the native core be replaced by LiteRT (Google AI Edge, ex-TFLite) for faster local inference on the OP7?
  cause: user research pass; LiteRT-LM advertises GPU/NPU acceleration, tool use, and on-device LoRA; local 1B Q4 model is ~1.8 tok/s (contended)
  solution: audited google-ai-edge/litert + LiteRT-LM v0.16.0 (2026-08-15). Hard incompatibility: runtime uses .tflite/.litertlm only, no GGUF path — a switch means a new export pipeline + second native runtime + APK growth, against the 1.5 GB gate and minimal-dependency rule. Decision: park as reference (ADR-007), revisit on trigger conditions: memory vector embeddings via small TFLite embedder; a same-device benchmark beating llama.cpp; Phase 9 LoRA evaluation. Any future integration goes behind the ModelProvider interface only.
  section: A
  tags: [litert, tflite, inference, adr, reference]

## Notes (optional)
- Facts recorded in docs/source-research/litert-lm.md: formats, runtimes (CPU XNNPack / GPU ML Drift / NPU), LiteRT-LM stable Kotlin/C++/Python APIs, MTP/speculative decoding, Google AI Edge Gallery as zero-code OP7 benchmark path, .litertlm int4 2B ~1.2-1.5 GB sizing risk.
- Unrelated to the open issue that free remote models 429 (anonymous per-IP rate limit) — that stays a provider/UX problem, not a runtime one.
