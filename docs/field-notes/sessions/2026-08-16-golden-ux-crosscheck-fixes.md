# Session digest — 2026-08-16 — Golden-UX cross-check: threading, state races, semantics

Follow-up audit of the golden-standard UX increment (ViewModel + approval gate +
a11y). Reviewed every new file for golden-standard violations before CI.

## Problems found & fixed
- **P** Agent/generate loops and model init ran on the Main thread.
  cause: ViewModel scope used `Dispatchers.Main.immediate`; the run coroutine
  inherited it, so llama.cpp decode / HTTP readLine / JNI init blocked the UI.
  solution: `runAgent`/`sendQuick` launch on `Dispatchers.Default` (UI updates
  only via thread-safe `StateFlow`); `ensureLocalLoaded` wraps native init in
  `withContext(Dispatchers.Default)`.
  tags: [threading, main-thread]

- **P** `RuntimeMetrics` was written from multiple contexts with plain fields.
  cause: metrics lived in the composable (`remember`), recreated on rotation,
  and were written from the run loop + load path.
  solution: metrics moved into the ViewModel (`val runtimeMetrics =
  RuntimeMetrics()` — survives rotation, EngineScreen reads `vm.runtimeMetrics`);
  all methods `@Synchronized`.
  tags: [metrics, threading, rotation]

- **P** Model-load state was contradictory: header showed COMPLETED right after
  a load; a failed re-init left the stale `loaded` flag true.
  cause: `ensureLocalLoaded` set `EngineUiState.COMPLETED` on success and never
  cleared `_loaded` on failure.
  solution: success → `READY`; failure → `_loaded = false`; init off Main.
  tags: [state, model-load]

- **P** Run-state races: double-send could overlap (flag set inside the
  coroutine), `runAgent` had no running guard, `stop()` double-appended
  `[STOPPED]` (stop + the coroutine catch both wrote it), `Clear` did not reset
  engine state, and the primary Agent button was disabled while running so its
  Stop branch was dead code.
  solution: `sendQuick` sets `_running` synchronously before launch;
  `runAgent` guards `if (_running.value) return`; `stop()` only cancels (the
  run's own CancellationException handler owns `[STOPPED]`/status);
  `clearRun` resets engineState/status/lastRoute/elapsed; primary button toggles
  Agent ↔ Stop and stays enabled; Clear disabled while running.
  tags: [state-machine, race, stop, clear]

- **P** Memory-screen budget card listed the models dir on the Main thread.
  solution: `withContext(Dispatchers.IO)` around the listing.
  tags: [memory, ui, io]

## Tests
- Added `clearRunResetsFullRunState` (engineState→READY, status/route/elapsed
  cleared). Existing ToolExecutor + ViewModel tests unchanged and still valid.

## Known limitations (not regressions)
- ViewModel state does not survive process death (only recreation) — full
  SavedStateHandle persistence is a separate increment.
- Stop during a pending approval can theoretically flash a second dialog before
  cancellation lands (edge window, `CompletableDeferred.await()` throws on the
  cancelled coroutine so it resolves DENY — safe, cosmetic only).
- `notify()` events with no active snackbar subscriber are dropped (buffer 8) —
  acceptable; collector is active for all user-triggered paths.
