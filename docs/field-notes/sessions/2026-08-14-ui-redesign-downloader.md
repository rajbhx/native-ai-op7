# Session digest — 2026-08-14 — Industrial UI redesign + in-app GGUF downloader

## Problems solved
- **P** "no way to use agent": with a remote model persisted but its provider not re-registered after process restart, and local not loaded, Agent had no routable model; local selection refused without an explicit Load first
  cause: providers for persisted remote selections were only registered on picker selection; local model required manual pre-load; messages were not actionable
  solution: MainActivity re-registers the persisted remote selection at startup; Agent/Generate auto-load the local GGUF on demand (one tap); error paths now say exactly what to do
  section: A
  tags: [agent, router, usability, startup]
- **P** getting a model onto the device required adb/shell (no standalone onboarding)
  cause: no in-app download path
  solution: ModelDownloader.kt (PocketPal-style guards: StatFs space check, redirect follow, progress, cancel, GGUF magic verify, tmp+rename) + "Download GGUF model" dialog with verified quick picks (Qwen2.5-0.5B/1.5B Q4_K_M, ModelScope/HF) + free-form URL
  section: A
  tags: [models, download, gguf, onboarding]
- **P** UI did not communicate real engine state or hierarchy (chat-like, not console-like)
  cause: single status string; actions/state not structured
  solution: header state chip driven by real operations (READY/LOADING/THINKING/TOOL/VERIFYING/COMPLETED/ERROR/OFFLINE); prompt as primary interaction (clear + send); CPU THREADS + CONTEXT selectors; AUTO/FREE/LOCAL/OFFLINE modes; SELECTED card with real connection state; structured agent trace rail; STOP SERVICE label; no fake status
  section: A
  tags: [ui, compose, redesign, state]

## Notes (optional)
- PocketPal AI (a-ghorbani/pocketpal-ai, React Native + llama.rn) audited; concepts adapted (not copied): download guards, per-device defaults idea, benchmark/params surfaces deferred. See docs/source-research/ui-architectures.md + native-inference.md.
- Verified on-device reachability: ModelScope 200 direct; HuggingFace 302 -> CDN 206 (works with redirects).
- CI green for redesign (6bbf4b9) and downloader (8312954). Device verification of both pending (phone in use).
