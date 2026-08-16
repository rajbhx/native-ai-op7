# Golden-Standard Source Catalog

Curated reference/code sources for the native-ai-op7 runtime, mapped to the
architecture layers. Every entry: what to take, license, and fit for the
OnePlus 7 / Android 10 / arm64-v8a / ≤1.5 GB dynamic RAM budget.

Legend — Status:
- **IN** = already integrated in this repo
- **PARKED** = vendored/probed but gated (benchmark or CI flag)
- **CANDIDATE** = ready to adopt when a phase needs it
- **REF** = reference only (license or weight forbids copying into the APK)

## 1. Inference runtimes

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT | Core GGUF engine, KV cache, quant formats, Android CMake flags (`GGML_BACKEND_DL`+`GGML_CPU_ALL_VARIANTS`) | Proven on SD855; already the engine | IN |
| [alibaba/MNN](https://github.com/alibaba/MNN) | Apache-2.0 | Intent classifiers + **on-device embeddings** (MNN-Converted small models), tuned ARM assembly | Fast on Qualcomm; tiny RAM for classifier/embedding models, not for 1B LLM | PARKED (dlopen probe, benchmark-gated) |
| [google-ai-edge/litert](https://github.com/google-ai-edge/litert) | Apache-2.0 | TFLite successor; NNAPI/GPU delegate for small models, embeddings, OCR-lite | NNAPI delegate works on API 29 (OP7); keep to <200 MB models | CANDIDATE |
| [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe) | Apache-2.0 | OCR / vision task graphs for future document tooling | Heavy; adopt only the minimal task graph, sized under budget | CANDIDATE (later, vision phase) |
| [huggingface/tokenizers](https://github.com/huggingface/tokenizers) | Apache-2.0 | Tokenizer parity for exotic formats | llama.cpp already tokenizes via GGUF; adopt only if a provider requires a non-llama tokenizer | CANDIDATE (rarely needed) |
| [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) | MIT | Fallback runtime for non-GGUF/non-MNN formats | Heavier APK; use only if a provider requires it | CANDIDATE (only if needed) |
| [pytorch/executorch](https://github.com/pytorch/executorch) | Apache-2.0 | Edge transformers | Too heavy for the 1.5 GB budget + 491 MB GGUF baseline | REF (avoid) |
| [qualcomm/qnn](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk) | Proprietary (free) | Hexagon DSP via NNAPI delegate on SD855 | Potential speedup; proprietary toolchain, keep behind capability probe | REF (optional, later) |

## 2. Memory / retrieval / search

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [sqlite/sqlite](https://github.com/sqlite/sqlite) | Public domain | FTS5 + BM25 full-text index (already core) | Platform SQLite; watch OEM FTS5 gaps (OP7 had "no such module: fts5") | IN |
| [unum-cloud/usearch](https://github.com/unum-cloud/usearch) | Apache-2.0 | HNSW vector index, single-header, JNI-friendly | ~0 extra runtime deps; hybrid BM25+HNSW (RRF) dormant until embedding gate passes | PARKED (vendored v2.11.3) |
| [spotify/annoy](https://github.com/spotify/annoy) | Apache-2.0 | HNSW alternative | USearch already vendored and probed — no reason to switch | REF |
| [MemPalace/mempalace](https://github.com/MemPalace/mempalace) | Verify | Spaced-repetition + memory-palace UI patterns for the Memory screen | Reference for review UX, not code | REF (verify license) |
| [langchain-ai/langchain](https://github.com/langchain-ai/langchain) | MIT | Memory/tool-chain composition patterns (RetrievalQA, memory backends) | Patterns only — heavy framework, never a dependency | REF |
| Android `FileObserver` / `WorkManager` | Apache-2.0 | Source re-index triggers + periodic refresh | uBO-style polling can stay off; events/WorkManager are battery-friendlier | CANDIDATE |

## 3. Sources / knowledge ingestion (Phase 4–8)

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [gorhill/uBlock](https://github.com/gorhill/uBlock) | GPL-3.0 | **Reference only**: asset-list tree + fetch/poll/expire logic, list metadata format | Proven pattern for source catalogs; do not copy GPL code — re-implement the model | REF |
| [jsoup/jsoup](https://github.com/jhy/jsoup) | MIT | HTML parsing/cleaning for web-page sources | Small JAR, JVM-friendly | CANDIDATE |
| [square/okhttp](https://github.com/square/okhttp) | Apache-2.0 | Downloads, GitHub API, SSE streaming, ETag/If-Modified-Since | Already the sensible HTTP layer | CANDIDATE |
| [apache/commons-compress](https://github.com/apache/commons-compress) | Apache-2.0 | Zip/tar extraction for repo-snapshot sources | Small; only when a compressed source type is added | CANDIDATE |
| [mozilla/readability](https://github.com/mozilla/readability) | Apache-2.0 | Article/main-content extraction | JS library — re-implement the heuristics with jsoup, do not bundle | REF |
| [TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | Apache-2.0 | PDF text extraction (replaces scratchdir hack) | ~few MB; only when a PDF source is added | CANDIDATE |
| [kurikomi-labs/komi-store](https://github.com/kurikomi-labs/komi-store) | Verify | GitHub OAuth/app-flow login reference | Reference for token acquisition UX; do not copy wholesale | REF (verify license) |
| [sst/opencode](https://github.com/sst/opencode) + [anomalyco/opencode](https://github.com/anomalyco/opencode) | MIT (verify fork) | Provider catalog + routing semantics for OpenCode-compatible free models | Mirror the catalog format; the app already talks OpenCode-compatible APIs | REF (fork: verify) |
| [a-ghorbani/pocketpal-ai](https://github.com/a-ghorbani/pocketpal-ai) | Verify | Local-LLM chat client patterns, GGUF download/verify UX | Reference for model manager UX (download → verify → use) | REF (verify license) |

## 4. Agent / terminal execution (Termux-inspired layer)

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [termux/termux-app](https://github.com/termux/termux-app) | GPL-3.0 + exceptions | **Architecture reference**: session model, process handling, terminal emulator patterns | GPL prevents embedding into this APK — clean-room interfaces only; optional separate app | REF |
| [termux/termux-api](https://github.com/termux/termux-api) | GPL-3.0 | Intent contract for hardware/tools from Termux | Same GPL constraint — adapter contract only | REF |
| [mudler/LocalAI](https://github.com/mudler/LocalAI) | MIT | Provider-agnostic local API mirroring | Patterns for model-manager/API-shape design | REF |
| [ollama/ollama](https://github.com/ollama/ollama) | MIT | Local model management + pull/verify UX | Patterns only; llama.cpp is the runtime, not a daemon | REF |

## 5. Observability / performance

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [square/leakcanary](https://github.com/square/leakcanary) | Apache-2.0 | Dev-time leak detection | Debug builds only (already wired) | IN (debug) |
| [JakeWharton/timber](https://github.com/JakeWharton/timber) | Apache-2.0 | Structured logging facade over logcat + CoreErrors | Tiny; adopt when log volume grows past the custom sink | CANDIDATE |
| [square/curtains](https://github.com/square/curtains) | Apache-2.0 | Window insets / IME tracking | Only if insets regress on OEM skins; current `imePadding` works | CANDIDATE (optional) |
| [Tencent/matrix](https://github.com/Tencent/matrix) | MIT (verify) | Resource/battery/IO monitoring in production | Heavy-ish; adopt the IO + battery modules only, gated | CANDIDATE |
| [Kyson/AndroidGodEye](https://github.com/Kyson/AndroidGodEye) | Apache-2.0 | Real-time CPU/RAM/network dashboard | Add behind Diagnostics only | CANDIDATE |
| [bytedance/btrace](https://github.com/bytedance/btrace) | Apache-2.0 | Startup / FPS / thread bottleneck tracing | Dev-tool only, not in release APK | CANDIDATE |
| [markzhai/AndroidPerformanceMonitor](https://github.com/markzhai/AndroidPerformanceMonitor) | Apache-2.0 | BlockCanary UI-jank detection | Small; dev builds | CANDIDATE |
| [mobile-dev-inc/maestro](https://github.com/mobile-dev-inc/maestro) | Apache-2.0 | UI journey + visual tests in CI | Pairs with GitHub Actions; keep tests on the smoke path | CANDIDATE |
| Platform: `simpleperf`, `Perfetto`, `logcat`, `am dumpheap` | Platform | Real device measurement — never fabricate numbers | Baseline discipline (field rule) | IN (workflow) |

## 6. UI / storage / misc (Apache-2.0 unless noted)

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [noties/Markwon](https://github.com/noties/Markwon) | Apache-2.0 | Render model answers / source hits as Markdown | Small, offline | CANDIDATE |
| [google/accompanist](https://github.com/google/accompanist) | Apache-2.0 | Insets/adaptive components where Material lacks them | Most insets now core; adopt only if a gap appears | CANDIDATE (optional) |
| [coil-kt/coil](https://github.com/coil-kt/coil) | Apache-2.0 | Image loading (model cards/avatars) | No image content today — avoid the APK weight | REF (until needed) |
| [Kotlin/kotlinx-serialization](https://github.com/Kotlin/kotlinx-serialization) | Apache-2.0 | Type-safe JSON | `org.json` covers current needs; adopt only when schemas grow | CANDIDATE (optional) |
| [square/retrofit](https://github.com/square/retrofit) | Apache-2.0 | Typed HTTP | OkHttp direct is already enough — no new layer | REF |
| [google/tink](https://github.com/google/tink) | Apache-2.0 | Key management if `security-crypto` proves limiting | Only if API keys ever persist | CANDIDATE (later) |
| androidx `security-crypto` (EncryptedSharedPreferences) | Apache-2.0 | API keys at rest | Replace plain prefs for keys | CANDIDATE |
| androidx `Room` / `DataStore` | Apache-2.0 | Structured state, versioned migrations | Deliberately **not** adopted — raw SQLite + prefs keep RAM/APK discipline; revisit only for migrations | REF (avoid for now) |
| [robolectric](https://github.com/robolectric/robolectric) | MIT | Android-context unit tests (ViewModels, prefs) | Unblocks Context-bound tests that JVM can't run | IN (test) |
| [mockk](https://github.com/mockk/mockk) | Apache-2.0 | Unit test doubles | Pair with Robolectric | CANDIDATE |
| [cashapp/turbine](https://github.com/cashapp/turbine) | Apache-2.0 | Assert `Flow`/`StateFlow` behavior in tests | Small, high value | CANDIDATE |
| [detekt](https://github.com/detekt/detekt) / [ktlint](https://github.com/pinterest/ktlint) | Apache-2.0 / MIT | Static analysis + formatting in CI | Cheap guardrails | CANDIDATE |

## 7. Agent / tooling patterns (reference only — never direct deps)

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [langchain4j/langchain4j](https://github.com/langchain4j/langchain4j) | Apache-2.0 | Java/Kotlin agent, tool, memory patterns | Mirror the contracts; the framework is too heavy for the budget | REF |
| [vercel/ai](https://github.com/vercel/ai) | Apache-2.0 | Provider-agnostic streaming + tool-call schemas | Schema patterns for OpenAI-compatible tools | REF |
| [openai/openai-openapi](https://github.com/openai/openai-openapi) | MIT | Tool-call / streaming API shapes | Contract reference for the provider adapter | REF |
| [FMHY/FMHY](https://github.com/FMHY/FMHY) | CC BY-SA 4.0 (verify pages) | Curated free-resource knowledge as a source KB entry | Already seeded as a SITE source; link content, don't bundle | IN (source) |

## 8. Build / CI / release

| Source | License | Take | Fit | Status |
|---|---|---|---|---|
| [gradle/android-cache-fix-gradle-plugin](https://github.com/gradle/android-cache-fix-gradle-plugin) | Apache-2.0 | Correct CI caches (fast=true pattern) | Cheap CI minutes; low risk | CANDIDATE |
| [ReactiveCircus/android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner) | Apache-2.0 | Emulator smoke on CI | arm64 APK cannot run on x86_64 emulators — use for JVM/UI tests only, or with a real device farm | CANDIDATE (test infra) |
| [fastlane/fastlane](https://github.com/fastlane/fastlane) | MIT | Release automation (sign, upload) | Phase-8 hardening, not now | CANDIDATE (release phase) |
| androidx `macrobenchmark` | Apache-2.0 | Startup/frame baselines in CI | Device-only; pairs with the Phase-7 sweep | CANDIDATE (device) |
| Platform: `adb`, `uiautomator`, GitHub Actions | Platform | Install/smoke/acceptance on the real OP7 | Only the phone proves arm64 behavior | IN (workflow) |

## Explicitly NOT in scope (golden-standard negative picks)
- **Forking Termux into the APK** — GPL-3.0 + APK/RAM weight; keep as optional external execution backend behind `ExecutionBackend`.
- **Bundling a full Linux distribution/pandoc** — GPL, huge, unnecessary; use jsoup + PdfBox-Android + Termux when present.
- **ExecuTorch / big ONNX graphs** — exceed the 1.5 GB dynamic budget next to a 491 MB GGUF.
- **Room migration now** — no schema-migration pain yet; raw SQLite + FTS5 already in place and tested.
- **Perf libs in release** — LeakCanary/Matrix/GodEye/Btrace must stay debug/Diagnostics-gated.
- **LangChain/langchain4j as dependencies** — patterns only; the frameworks are oversized for the budget.

## Adoption order (next phases)
1. ~~Robolectric~~ ✅ IN — Context-bound unit tests now run on the JVM (ModelPreferences).
2. Skill management UI — ✅ done (Phase 10 basics: create/update/delete, built-ins read-only).
3. OkHttp + ETag refresh — Source Updater battery/efficiency (uBO model, re-implemented).
4. jsoup + PdfBox-Android — Phase 5 ingestion breadth.
5. Markwon — render answers/sources.
6. security-crypto (or Tink) — API keys at rest, only if keys ever persist.
7. Timber — logging facade when the custom sink outgrows itself.
8. Maestro — CI smoke UI journeys (device farm or debug APK).
9. android-cache-fix + fastlane — release hardening (Phase 8).
10. MockK + Turbine — richer test doubles when the suite grows.
