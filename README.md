# native-ai-op7

Native self-learning agentic AI engine for Mobile/Edge — OnePlus 7 edition
(Snapdragon 855: Kryo 485, Adreno 640, 6 GB RAM, UFS 3.0).

Stack: Kotlin (Foreground Service) + C++17 (llama.cpp JNI bridge, Vulkan
offload) + SQLite3 (FTS5 + vector memory) + CMake. arm64-v8a only.

**Builds run on GitHub Actions, never locally.** Iterate with
`workflow_dispatch` on `.github/workflows/build.yml`; releases come from
validated runs only. Phase status is tracked in the OP7 Special-Build
Playbook: `projects/native-ai-op7/roadmap.md`.

Rules (from the playbook):
- Baseline before optimization; measure on the real device.
- One measured optimization per revision; benchmark before/after; revert on regression.
- Never publish an unvalidated build.
- Free infra only: GitHub Actions/Releases/caches (+ Hugging Face for GGUF/LoRA artifacts).

## Layout
- `app/src/main/cpp/` — JNI bridge + CMake (llama.cpp submodule, Vulkan, OpenMP)
- `app/src/main/java/com/engine/nativeai/` — Kotlin: `NativeEngine` (Phase 1)
- `.github/workflows/build.yml` — GitHub Actions build (fast=debug variant)
- `docs/GOLD-STANDARD-SPEC.md` — the full engineering spec (audit-first, 1.5 GB ceiling, security model)

## Pinned dependency
- `third_party/llama.cpp` — submodule pinned to release **b10428** (`885c5bb`),
  shallow. Native code is written ONLY against the API in that exact checkout
  (verified: no obsolete symbols). Do not bump silently.

## Status
DRAFT skeleton. Per the spec's FIRST TASK the audit comes before code:
llama.cpp API (done for the pinned commit), NDK/toolchain, Adreno 640 Vulkan,
KV-cache quantization availability, and the 1.5 GB budget feasibility.
Phase 1 = make the CI build green + streaming generation.
See the playbook roadmap for the full phase plan.
