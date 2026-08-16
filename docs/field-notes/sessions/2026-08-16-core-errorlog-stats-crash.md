# Session digest — 2026-08-16 — Full on-device test + core ErrorLog/ERRORS tab

## Problems solved
- **P** opening Diagnostics (Stats) killed the app on-device.
  cause: `UnsatisfiedLinkError: No implementation found for boolean
  com.engine.nativeai.USearchVectorIndex$Companion.nativeSelfTest()` —
  the C++ export was `Java_com_engine_nativeai_USearchVectorIndex_nativeSelfTest`
  but the Kotlin `external fun` lives in the companion object, so JNI needs
  the `_00024Companion_` infix; the throw was uncaught in the composable.
  solution: renamed the JNI export to
  `Java_com_engine_nativeai_USearchVectorIndex_00024Companion_nativeSelfTest`,
  wrapped `selfTest()` in `runCatching { }.getOrDefault(false)` (fail
  closed), and wrapped both MNN + VECT diagnostics probes in `runCatching`
  with a `CoreErrors` record on failure. Diagnostics can no longer crash.
  section: A
  tags: [jni, crash, diagnostics, usearch, companion-object, stats]

- **P** Sources rows showed `ERROR · The coroutine scope left the
  composition` after seed refresh.
  cause: the S3 startup `updateOnce()` ran in a composition-scoped
  `LaunchedEffect`; navigating away cancels the scope and the
  `CancellationException` was caught as a normal failure, marking the row
  errored.
  solution: `SourceUpdater` now rethrows `CancellationException` (a cancel
  is not a source error — the next cycle retries cleanly) and records real
  failures into `CoreErrors.log`.
  section: B
  tags: [coroutines, cancellation, sources, scope, state]

- **P** app was LMK-killed once while backgrounded with the local model
  loaded on the 8 GB device.
  cause: model stays resident in the foreground service while backgrounded;
  the kernel reclaimed the process under memory pressure.
  solution: accepted behavior for now — status line and service state
  machine report honest state on resume; unload-on-background is a future
  Phase 14 item (do not chase it inside this build).
  section: A
  tags: [lmk, memory, background, service]

- **P** failures were invisible: handled errors set a status string that
  scrolled away, and uncaught crashes just killed the process.
  cause: no central failure surface existed.
  solution: core `ErrorLog` (bounded, thread-safe, cap 100) + process crash
  hook in `NativeAiApp` + `CoreErrors` singleton; agent/generate/
  local-init/diagnostics/source paths record structured entries
  (timestamp, source, message, detail). Main screen LOG ZONE gained a third
  tab `ERRORS · N` with CLEAR/REFRESH and expandable monospace detail rows.
  Tests: `ErrorLogTest` (order, cap, clear, truncation, null).
  section: B
  tags: [error-log, observability, crash-hook, ui, errors-tab]

## On-device results (installed build = main a217dd7)
- Passed: local quick-send (PARIS answer, 321 chars), Load button
  (READY · LOCAL · GGUF), settings sheet (tiers, honest capabilities,
  tool inventory terminal DISABLED, hardware pickers, service
  STOPPED/READY, dynamic budget est 772 MB / cap 1536 MB), Sources seed rows.
- The Stats crash and Sources error above were the two real bugs; both fixed
  on `ci/core-errorlog` (verified by CI, NOT installed on the phone yet —
  user approval pending).
