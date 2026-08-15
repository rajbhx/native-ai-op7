# Roadmap — UI / basic-function fixes (Track A)

One logical change per revision; CI green -> on-device verify -> field note
-> playbook sync. Keeps the OnePlus dark identity and red-accent hierarchy.

## R1 — Send / IME (done)
- Prompt field: `imeAction = Send` + `KeyboardActions(onSend = sendQuick)`.
- One shared `sendQuick()` for the Send button, IME action, and future
  hotkeys; no duplicated completion logic.
- Send button: labeled "Send" (>= 56dp touch target, contentDescription,
  dimmed when prompt blank or running) replacing the 48dp circle arrow.

## R2 — Engine service state (done)
- `EngineForegroundService` exposes `StateFlow<EngineServiceState>`
  (STOPPED / STARTING / READY / BUSY / ERROR) on the companion.
- UI collects it instead of a local `serviceOn` guess; Start/Stop reflects
  the real service lifecycle (STARTING set in onCreate, READY after init,
  STOPPED in onDestroy).

## R3 — Stop hardening (done)
- Stop button calls `stopRun()`: cancels the run job, clears run state,
  records `[STOPPED]` in the trace, returns to READY.
- `ExecutionBackend.shutdown()` (default no-op) + `LocalProcessBackend`
  tracks live processes and destroys them on shutdown so no orphaned
  shells survive service teardown.

## R4 — Docs
- This file; ROADMAP.md updated after the wave.

## Not in this track (later phases)
- Tools panel + terminal allowlist UI, chat history, system prompt editor,
  Vulkan/NNAPI toggles, diagnostics export.
