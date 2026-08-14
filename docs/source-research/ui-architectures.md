# Source research — UI architectures

## Hermes WebUI / OpenClaw UI concepts
- Streaming agent event feed (task -> plan -> tool -> observation -> verify ->
  final) is the core "visibly agentic" pattern.
- Model/availability metadata shown as live data, never assumed.

## Native Android adaptation (NEVER SETTLE prompt)
- Design tokens: OnePlus red #EB0029, pitch black #121212, charcoal card
  #1E1E1E, slate text #A0A0A0, divider #2A2A2A.
- Screen 1 Model Hub: dynamic model cards (name, provider pill, Free tag,
  context/tools/ratings, availability), segmented mode selector
  [Auto | Free-First | Offline Only].
- Screen 2 Agent Trace: live ReAct feed with Horizon Light edge pulse during
  generation.
- No Compose dependency: plain XML + custom drawables keeps the APK small and
  the UI sub-100 ms responsive (playbook constraint).

## PocketPal AI (a-ghorbani/pocketpal-ai, React Native + llama.rn)
- **Problem**: polished mobile app for running GGUF models fully locally.
- **Learn (UI/UX)**: model onboarding without adb — in-app GGUF downloader
  (HF + custom download module) with progress, cancellation, free-space check,
  and a "don't promote partial files" rule; per-device defaults
  (`services/deviceRules`); Benchmark screen recording n_ctx/n_threads/
  n_gpu_layers result cards; Chat screen with streaming tokens and suggested
  prompts; completion-params UI (temp/top_k/top_p/min_p/xtc) with validation
  ranges and defaults.
- **Do NOT copy**: React Native/Flutter stacks, WatermelonDB, MobX, the whole
  download-module native layer — all too heavy for the OP7 runtime; we stay on
  Compose + raw SQLite + HttpURLConnection.
- **Adapted here**: `ModelDownloader.kt` (GGUF magic check, space check,
  cancel, tmp+rename) + "Download GGUF model" dialog with verified OP7-sized
  quick picks (Qwen2.5-0.5B/1.5B Q4_K_M via ModelScope/HF); context selector
  (512/1024/2048) wired into EngineConfig; threads selector already present.
- **Deferred**: completion-params sheet (temp/top_k/top_p), benchmark screen
  UI (perf logs exist via NativeEnginePerf), device-rule defaults.
