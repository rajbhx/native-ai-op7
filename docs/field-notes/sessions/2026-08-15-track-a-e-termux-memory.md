# Session digest — 2026-08-15 — Track A UX fixes + Track E Termux core + dynamic memory

## Problems solved
- **P** Send relied on a tiny 48dp circle button and there was no IME action
  or unified quick-completion path; stop only cancelled the job without state.
  cause: prompt field had no KeyboardActions; completion logic was inlined in
  one button; Stop did not reset UI state or record the abort.
  solution: `imeAction=Send` + `KeyboardActions(onSend=sendQuick)`, one shared
  `sendQuick()`; labeled 56dp Send button with contentDescription; Stop ->
  `stopRun()` (cancel, clear state, append `[STOPPED]` to trace).
  section: A
  tags: [ui, ime, stop, kotlin, compose]
- **P** the UI guessed service state with a local `serviceOn` boolean.
  cause: `EngineForegroundService` exposed no state.
  solution: `StateFlow<EngineServiceState>` (STOPPED/STARTING/READY/BUSY/ERROR)
  on the service; UI collects it. Enum must be top-level — nested-in-companion
  enums are NOT promotable (`Class.Enum` fails to resolve, e:149).
  section: A
  tags: [service, stateflow, enum, kotlin]
- **P** Termux was only an abstraction (`ExecutionBackend`), no backend ran
  through Termux; user: "make termux implementation available at core level".
  cause: audit parked `TermuxBackend` as next-phase.
  solution: `TermuxBackend` (clean-room) — `com.termux` detection via
  PackageManager + `<queries>`, public `com.termux.RUN_COMMAND`
  RunCommandService intent, file-exchange output under
  `/sdcard/Download/nativeai/<runId>/` (out/err/code), hard timeout,
  cancellation, best-effort `pkill -f nativeai-<runId>` (run tag in argv),
  cleanup; `TermuxStatus` (NOT_INSTALLED/INSTALLED/SETUP_REQUIRED/READY/ERROR)
  with honest reasons; `ExecutionManager` picks Termux when READY else local;
  Tools settings row (ON/OFF, allowlist editor, Test connection); prefs
  terminalEnabled/terminalAllowlist; `READ_EXTERNAL_STORAGE` maxSdk 32.
  section: A
  tags: [termux, execution, intent, tool, backend]
- **P** "1.5 GB memory" was read as a static carve-out; user clarified it is
  a hard MAXIMUM with dynamic sizing below it.
  cause: budget was a fixed breakdown table.
  solution: `MemoryPlanner` — `cap = min(availRAM*(1-0.15), 1536 MB)`; sizes
  KV/context dynamically (maxSafeNctx solver), pre-flights model load and
  rejects with an actionable message instead of crashing; dynamic-budget hint
  under the context pills.
  section: A
  tags: [memory, planner, budget, oneplus]
- **P** CI compile/test failures after Track A/E landed.
  cause 1: nested enum in companion not accessible as `Class.Enum`.
  cause 2: constructor param `context` referenced inside a method body
  (param without `val` is init-only).
  cause 3: appended JUnit test landed OUTSIDE the class -> file-facade
  `ExecutionLayerTestKt` -> `InvalidTestClassError` (initializationError).
  solution: top-level enum; `private val context`; tests stay inside the
  class body. APK build and all 104 JVM tests then pass.
  section: A
  tags: [kotlin, enum, junit, compile, ci]
