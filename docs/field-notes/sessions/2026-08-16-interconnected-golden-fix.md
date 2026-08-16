# Session digest — 2026-08-16 — Interconnected golden fix (P1–P7)

Code-only pass (no build, no push) making routing, continuity, storage,
sources, tools and error handling share one consistent state. Addresses the
7-problem audit plus "tool inventory must reflect offline/online + model
state".

## P1 — Honest routing fallback
- `ModelRouter.route` gained `preferKind`; same-kind candidates are tried
  first, the full pool only opens when none remain.
- `ThinkingAgent` fallback passes the failed model's kind and always emits
  an honest `Routed(reason)` (`<failed> failed; using <fallback>`), so a
  local pick can no longer silently become a rate-limited remote.
- `EngineViewModel.friendlyGenerateError` translates rate-limit / 401 /
  timeout / network failures into actionable text (raw message otherwise).

## P2 — web_search honesty
- `SearchProvider.configured` (fallback = false); `WebSearchTool.available`
  reflects it with `unavailableReason`; `AgentTool`/`ToolDescriptor` gained
  `unavailableReason`; inventory shows NOT CONFIGURED + reason, and OFFLINE
  when the network is down.

## P3 — Conversation continuity
- One `activeSessionId` shared by `sendQuick` + `runAgent`; Clear closes the
  thread (new conversation). Exchange saved on success, error and stop;
  `MemoryDatabase.conversationTail()` supplies chronological prior turns.

## P4 — Storage choice (scoped-storage honest)
- New `StoragePaths` = single resolution point (data dir, models dir,
  catalog, memory DB path, SAF tree→path, labels).
- `ModelPreferencesStore` gained `dataDirOverride`/`modelsDirOverride`;
  ENGINE SETTINGS → STORAGE rows (Internal / External / Pick via
  `OpenDocumentTree`, app-scoped only). Models-dir changes apply live
  (library rebuild + VM re-attach); data-dir changes need a restart (DB is
  opened at startup). Toolbox, MemoryScreen, SourcesScreen, service and
  MainActivity all resolve through `StoragePaths`.

## P5 — Sources readable
- View button per source + clickable KNOWLEDGE HITS →
  `SourceViewerDialog` (files, expandable chunks, show-more, copy-all).
- `SourceStore.sourceFileById` implemented in `MemoryDatabase` and the
  `JdbcSourceStore` test twin.

## P6 — GGUF import progress
- `importProgress` drives a `LinearProgressIndicator`; unknown-size imports
  labelled "(unknown size)"; failures raise a snackbar; success points to
  Load Model.

## P7 — Interconnect
- Tool inventory recomputes on network / termux / selection / storage
  changes (3s poll while the sheet is open); models dir + VM library
  re-point together; storage pickers persist through the same prefs the
  router and agent read.

## Tests
- `ModelRouterTest`: same-kind fallback (local→local, remote→remote, and the
  documented full-pool fallback when no same-kind remains).
- `WebSearchToolTest`: availability + unavailableReason.
- `ModelPreferencesTest` + `ModelPreferencesRobolectricTest`: new storage
  override fields (defaults, round-trip, null-clear).

## Verification
- Brace/paren balance on all 22 touched files passed.
- No local SDK — CI is the compile gate; build is manual
  (`workflow_dispatch`) and NOT dispatched from this session.

## Next
- User approval → manual CI build → reinstall smoke test (signature stable
  since the rotation; `pm install -r` works going forward).
