# Session digest — 2026-08-14 — Phase 4 foreground service + UI/README

## Notes
- EngineForegroundService (specialUse FGS): holds NativeEngine + MemoryDatabase
  so OxygenOS doesn't kill the native process; RSS watchdog loop every 10 s
  against the 1.5 GB ceiling (honest reporting); learning eligibility gate
  every ~5 min via SelfLearningPipeline.exportVerifiedTrainingData +
  triggerBackgroundFinetune — never trains silently.
- Manifest: FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE +
  POST_NOTIFICATIONS permissions; service declaration with
  PROPERTY_SPECIAL_USE_FGS_SUBTYPE.
- Compose UI: Start/Stop service toggle (with error handling),
  POST_NOTIFICATIONS runtime request (API 33+), agent runs open/close a
  session row in the sessions table.
- README rewritten to current status (phases through Phase 4 service, math
  spec, Compose UI).
