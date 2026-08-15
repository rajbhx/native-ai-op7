# Session digest — 2026-08-15 — On-device exec verify + crash fix

## Problems solved
- **P** the Engine Settings bottom sheet content did not scroll on the
  phone (1080x2400): HARDWARE TUNING, Start service, Download GGUF, Stats
  and TOOL INVENTORY sat below the fold and were unreachable, so the
  buttons looked "missing" and Download/Stats could not be exercised.
  cause: `ModalBottomSheet` body `Column` had no scroll modifier; the
  sheet is full-height (`skipPartiallyExpanded`), so taller-than-screen
  content was simply clipped.
  solution: added `.verticalScroll(rememberScrollState())` to the sheet
  Column (verified reachable in the next build).
  section: A
  tags: [ux, bottom-sheet, scroll, settings, buttons]

- **P** app SIGSEGV'd (native, fault addr 0x0) in
  `ggml_compute_forward_rope` (`libggml-cpu-android_armv8.2_2.so`) ~2 s
  after "agent stopped" while running Qwen2.5-0.5B locally, killing the
  whole process (engine service runs in the same process).
  cause: `stopRun()` called `runJob.cancel()` but immediately released
  `running`/`runJob`. The local generation is a blocking llama.cpp decode
  inside `callbackFlow`; coroutine cancellation only closes the flow
  (which fires `nativeCancel`) — the native loop unwinds on the *next*
  sampled token (~0.5-1 s). Starting a new run in that window overlapped
  two generations on one `llama_context`, corrupting the graph.
  solution: `stopRun()` now calls `engine.cancel()` first (stops the
  native loop promptly) and keeps `running=true` until the job's `finally`
  unwinds, so a new run can never overlap a still-decoding engine. Both
  quick-send and agent paths reset `running` in `finally`.
  section: A
  tags: [crash, sigsegv, rope, stop, concurrency, native, llama]

- **P** startup status line was a stale constant ("Engine library loaded.
  Put a GGUF in: …") shown even when `models/model.gguf` existed.
  cause: `status` was initialized once with a fixed string, never
  reflecting the actual library scan.
  solution: initialize from `LocalModelLibrary.scan()` result: empty →
  guidance to Download/import; non-empty → "Local library ready: <files>".
  section: A
  tags: [ux, status, local-library, contradiction]
