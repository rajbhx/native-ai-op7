# Session digest — 2026-08-16 — Golden-standard UX: ViewModel state survival, tool approval gate, feedback, a11y

Scope: code only, not pushed. Builds/verification happen on CI (no local SDK).
Baseline: audit of `EngineScreen.kt` showed ~20 `remember { mutableStateOf }`
state slots, no snackbar/confirm feedback, no in-loop tool consent, IME/insets
unhandled, and sub-48dp touch targets.

## Problems solved
- **P** "When I clicked no 'Agent' it crashed": agent runs lived in a
  composition coroutine; an uncaught exception killed the process with no
  in-app trace.
  cause: run state owned by the composable; exceptions escaped to the
  process-level crash hook instead of the ERROR surface.
  solution: `EngineViewModel` is now the single authority (one-way data
  flow); agent/generate/local-init exceptions are caught, recorded to
  `CoreErrors.log`, and surfaced in the ERRORS tab + status line.
  section: A
  tags: [viewmodel, crash, agent, error-log, state]

- **P** Rotation/process death silently cancelled an active run and wiped
  prompt/output/trace; `rememberSaveable` covered only two booleans.
  cause: ~20 `remember { mutableStateOf }` slots in the composable.
  solution: `EngineViewModel` + `StateFlow` (prompt/output/answer/running/
  engineState/status/loaded/elapsed/lastRoute/pendingApproval); run coroutine
  lives in the ViewModel scope so a run survives recreation; UI only renders
  and calls methods.
  section: A
  tags: [viewmodel, stateflow, configuration-change, state]

- **P** No in-loop tool approval UX: `PermissionManager` auto-denied
  REQUIRES_APPROVAL/PRIVILEGED with no consent path.
  cause: `ToolExecutor` decided alone; UI never saw the request.
  solution: `ToolApprovalRequest`/`ApprovalDecision` + injected `onApproval`
  callback (default DENY — behavior unchanged without a callback);
  `EngineViewModel.approvalCallback` suspends the run, sets
  `pendingApproval`, and persists ALWAYS_ALLOW in `ModelPreferences.toolAlwaysAllow`;
  EngineScreen renders an Allow once / Always / Deny dialog. PRIVILEGED stays
  policy-blocked — the model can never bypass it.
  section: A
  tags: [tools, approval, permissions, consent, state]

- **P** Zero transient feedback: successes/failures only landed in a scrolling
  status line; destructive actions had no confirm/undo.
  cause: no snackbar layer, no confirm dialogs.
  solution: `UiEvent` SharedFlow + `SnackbarHost` (Retry/Undo actions);
  confirm dialog for model deletion; `PillButton` gains a running/loading
  spinner and the primary Agent button becomes Stop while running.
  section: A
  tags: [snackbar, feedback, confirm, loading]

- **P** IME/insets unhandled: bottom sheet and prompt field sat under the
  nav bar/keyboard on Android 10 gesture nav.
  cause: no `imePadding`, no `adjustResize`.
  solution: `windowSoftInputMode="adjustResize"` on MainActivity +
  `imePadding` on the screen root.
  section: A
  tags: [ime, insets, android-10, ux]

- **P** Accessibility gaps: sub-48dp touch targets, no state announcements.
  cause: text-based pills and chips with 5dp vertical padding; header status
  was color-only.
  solution: 48dp min heights on PillButton/ValuePill/FilterChip;
  `stateDescription` + elapsed on the header, `liveRegion` on status,
  `contentDescription` on prompt send/clear and the model chip.
  section: A
  tags: [accessibility, a11y, touch-target, semantics]

- **P** Memory screen had no per-component budget visualization.
  cause: budget math existed (MemoryBudget) but only as a text line.
  solution: `ModelMemoryBudget` card on the Memory screen: weights
  (measured from the largest local GGUF), KV (estimated at 2048 ctx),
  graph/sqlite (fixed profile constants) bars vs the 1536 MB cap, labeled
  measured vs estimate.
  section: A
  tags: [memory, budget, ui, bars]

## Tests added (JVM, no SDK needed)
- `ToolExecutorTest`: deny-by-default, allow-once runs, deny stops, PRIVILEGED
  never bypassed even with approval, safe tools skip the gate, cancellation
  propagates (not swallowed), timeout returns structured error.
- `EngineViewModelTest` (MainDispatcherRule): initial state, setters,
  clearRun, sendQuick guards (blank prompt / not attached), stop/resolve
  no-ops, onCleared, notify event flow.

## Not verified on device (needs CI/phone)
- Remote-vs-local latency ("remote feels slow") — untouched this turn; needs
  router latency/priority work.
- End-to-end approval dialog rendering and run-survives-rotation on a real
  OP7 — covered by CI build + smoke checklist only.
