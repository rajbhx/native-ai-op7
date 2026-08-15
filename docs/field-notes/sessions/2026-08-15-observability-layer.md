# Session digest — 2026-08-15 — Observability layer (metrics/diagnostics/jank)

## Problems solved
- **P** the app had no observability layer: `AgentEvent` carried no
  timestamps, tool durations/tok/s were not tracked, and the Stats button
  only dumped `memoryStats()` to the status line.
  cause: no metrics contract; trace events were plain data; no jank/leak
  tooling.
  solution: `RuntimeMetrics` (load, first-token, tok/s, per-tool timings,
  errors/retries, service restarts, dropped/janky frames) + `DiagnosticsProvider`
  interface with `RuntimeDiagnostics` + `DiagnosticsDialog` (Stats button);
  `AgentEvent` gained `atMs`/`durationMs`; trace lines render `HH:mm:ss` and
  `TraceLine` strips the prefix before classification; `FrameJankMonitor`
  (Choreographer, no dependency); LeakCanary `debugImplementation` only;
  `.maestro/acceptance/` flows run on the physical device (arm64-only APK
  cannot run on x86_64 CI emulators — honest constraint); reference +
  license notes added.
  section: A
  tags: [observability, metrics, diagnostics, jank, leakcanary, maestro]
