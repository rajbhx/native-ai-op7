# Session digest — 2026-08-15 — UX/execution-layer verification

## Problems solved
- **P** chip showed a model name + "not selected" — contradictory, and the
  persisted remote selection silently fell back to local after restart.
  cause: discovered catalog never persisted (`saveCatalog` uncalled) and
  remote providers were not re-hydrated at startup; the UI then rendered a
  name with "not selected".
  solution: save the catalog after discovery + `RemoteProviderBootstrap`
  (register providers for every remote descriptor, re-seed a missing
  persisted zen id); router prefers the explicit selection over transient
  health marks; Send now routes through the router with the selected model
  instead of always running local. UI defaults an unresolved selection to
  the local model, so the chip never shows a ghost name.
  section: A
  tags: [router, persistence, ui, bootstrap, discovery]
- **P** long prompts could not be typed reliably for testing.
  cause: `shizuku input text` mangled long strings (IME autocorrect) and
  Android 10 shell has no clipboard API.
  solution: `MainActivity` seeds the prompt field from `--es prompt` intent
  extra; verified on device.
  section: A
  tags: [automation, intent, testing]
- **P** CI builds re-signed with a fresh keystore -> installs wiped app data
  (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
  cause: AGP generated a random ~/.android/debug.keystore per runner.
  solution: persist keystore base64 in DEBUG_KEYSTORE_BASE64 secret; restore
  + keytool-verify before assembleDebug; installs now update in place.
  section: A
  tags: [ci, keystore, install, gha]
- **P** Termux was suggested as a tool-execution host.
  cause: user reference; upstream termux-app is GPL-3.0-only.
  solution: clean-room interface layer only (audit:
  docs/source-research/termux-execution-audit.md); `ExecutionBackend`,
  `LocalProcessBackend`, `ExecutionPolicy` (deny-all default),
  `TerminalTool` disabled by default; `AgentTool.available` keeps off tools
  out of the model's tool prompt.
  section: A
  tags: [termux, execution, policy, clean-room, license]

## Test results (2026-08-15 late session)
- **Flow transparency crash fixed + CI green** (run 31861231235). Remote
  quick-send previously died with "generate failed: Flow exception
  transparency is violated" because `OpenAICompatibleProvider.stream` emitted
  inside `withContext(Dispatchers.IO)` (and from a bare catch block) inside the
  flow builder. kotlinx.coroutines 1.8 bans context-mismatched emissions.
  Fixed with `flow { IO work }.flowOn(Dispatchers.IO)` + `.catch { emit(Error) }`.
  Verified locally (kotlinc + coroutines jar + local SSE stub: tokens Hi/ there
  + Done; unreachable endpoint → Error) and via CI regression tests.
- **Signing saga (accept-new-signature outcome).** Every CI APK got a fresh
  random debug key (certs 0b045c7c → 64ced54c → 7f721a2c) despite restoring
  ~/.android/debug.keystore; JKS vs JDK17-PKCS12 default + AGP silent
  regeneration. Explicit signingConfig also failed to load. Per user decision:
  stopped fixing, uninstalled, installed fresh APK (new signature), restored
  491 MB model from /sdcard/Download/nativeai-model-backup.gguf via run-as.
- **On-device verification of remote generation with the fixed APK is PENDING**
  (app just reinstalled + model restored; remote Send + tests 1-3 + remote
  persistence still to verify with logcat evidence).

## Test results (2026-08-15 mid session, fixed build fc2b8f7, new signature)
- **Test 1 (math -> calculator routing) VERIFIED with DB-level proof.** Fresh
  install + model restored (491 MB Qwen GGUF). Session row + 3 `tool_results`
  rows: session 1 = Test 1 prompt ("Calculate the exact model weight memory
  footprint ... Use the calculator tool."), calculator attempts at 09:45:54 /
  09:50:51 / 09:56:35 IST, ALL `ok=0` with empty output. `NativeEnginePerf`
  (device idle, uncontended): gen1 256 tok @1.56 tok/s, gen2 @1.46, gen3 @1.27
  (prompt evals 110-201 s). The verifier detected each `ok=0`, the loop
  REPLANNED 3x, then hit the iteration limit and finalized -> the
  PLAN/EXECUTE/OBSERVE/VERIFY/REPLAN state machine works on-device.
- **P calculator tool fails 0/3 (routing works, execution does not).**
  cause: `ToolExecutor` persists only `output` (empty on failure) - the error
  message and the model's raw expression are lost; `ActionParser` requires
  strict JSON `{"action","input"}` and `SafeExpr` rejects everything but
  `+ - * / ^ ( )` and plain decimals (no `e`-notation, units, commas), so a
  1B model's sloppy expression fails silently. solution (proposed, not yet
  implemented): persist `error` + input in `tool_results`, log tool
  inputs/errors to logcat, and extend SafeExpr with `e`-notation/commas;
  one revision, CI + on-device verify required.
  section: A
  tags: [calculator, tool, observability, agent-loop, replan]
- **P heating: background generation pegs 4 Kryo cores at max freq.**
  cause: an agent run re-triggered by a manual Agent tap (prompt persists in
  the field; no auto-run path exists) keeps llama threads at 100% with no
  stop/abort and nothing cancels on backgrounding. Measured (contended):
  `com.engine.nativeai` 135% CPU in background, 4 DefaultDispatch threads
  ~96-103% each, TID 14176 accumulated 18:09 CPU in ~21 min of process life,
  cpu7 @ 2.84 GHz max + cpu4-6 @ 2.42 GHz max, CPU zones 78-85 C, GPU 49.6 C,
  battery 43.4 C, skin 46 C, thermal status 0 (no throttle), system RAM
  451 MB free + 915 MB swap used. After `am force-stop`: CPU zones 52-56 C
  (-30 C) within 90 s, cpu7 -> 826 MHz idle. mitigation: force-stop; fix =
  PLAN-NEXT-PHASE Phase A3 Stop control + cancel on backgrounding.
  section: A
  tags: [thermal, cpu, background, stop, agent-loop]
- **FTS5 crash fix (fc2b8f7) verified on-device.** The pre-fix crash text is
  captured in /tmp/op7-t1e.xml ("agent failed: no such module: fts5"); the
  fixed build ran the full Test 1 loop (3 tool attempts + finalize) with no
  memory crash. Note: experiences table still empty after Test 1 - STORE
  did not persist a memory row (investigate later).
- **Tests 2 & 3 PENDING (deferred).** Device in active use by the user; long
  pinned-core generations would heat the device and steal the screen.
  Test 2's routing evidence already exists from an earlier build
  (/tmp/op7-t1b.xml: UNDERSTAND -> big-pickle/opencode-zen REMOTE -> PLAN ->
  local-llama -> FINALIZE -> STORE -> FINAL, header COMPLETED) but must be
  re-run on the fixed build for the record.

## Revision d753a22 — calculator parsing + tool-result observability (CI green)
- **P Test 1 calculator 0/3 failure mode could not be diagnosed.**
  cause: `ToolExecutor` persisted only `output` (empty on failure) and a bare
  input hash; the model's expression and the tool error were lost. SafeExpr
  also rejected exponent notation and grouped numbers (`1e9`, `1,000,000`),
  which 1B models emit routinely for "3 billion * 4.5 bits" style math.
  solution: schema v2 (`tool_results.input`, `error_summary` columns,
  SCHEMA_VERSION 2, dev drop-recreate migration) + `SafeExpr` e-notation and
  comma thousands separators. Verified standalone with kotlinc: 10/10 checks
  pass (new + existing behaviors); CI run 31865457792 GREEN (compile + full
  JVM suite). On-device Test 1 re-run PENDING (device in use) - expect the
  next run to record real input/error rows and succeed on expressions like
  `3 * 4.5e9 / 8`.
  section: A
  tags: [calculator, safeexpr, observability, schema, ci]
- **Vision re-anchored to the original memory-first master prompt.**
  Added docs/VISION-MEMORY-FIRST.md (requirement-by-requirement map, 20 rows:
  shipped/partial/planned) + ROADMAP north-star pointer. Key gaps now ranked:
  conversation store + Memory screen (M1), knowledge update/OUTDATED (M2),
  WebResearchEngine (M3), ResearchAgent (M4), training schema v2 (M5).
  section: A
  tags: [vision, roadmap, memory-first, plan]

## Revision f5fafd1 — M1a conversation store (CI green)
- **P no conversation history was persisted; the app reset every run.**
  cause: sessions existed but no messages table; user prompts and answers
  lived only in UI state (matches earlier UX finding "no chat history").
  solution: schema v3 adds `messages` (session_id/role/content/created);
  `recordMessage`/`recentMessages`/`searchMessages` (LIKE fallback, matching
  the FTS5-less OP7 SQLite path); EngineScreen persists user prompt at
  session start and the final answer on AgentEvent.Final (best-effort,
  wrapped so memory failure never crashes the agent). SQL validated locally
  against a schema replica (ordering + LIKE hits). CI run 31865939332 GREEN.
  On-device restart-persistence proof PENDING (device in use): run a prompt,
  force-stop, reopen -> messages rows must survive in `databases/memory.db`.
  section: A
  tags: [memory, conversations, schema, persistence, m1]

## Revision 8c8a4c1 — M1b Memory screen (CI green)
- **P memory was invisible in the UI; sessions existed but could not be
  browsed or searched.**
  cause: no read surface for messages/experiences/facts; the main screen is
  single-turn.
  solution: `MemoryScreen` (search field + grouped CONVERSATIONS/EXPERIENCES/
  FACTS results + recent-sessions browse + per-session message history,
  restored from disk) reached via a compact MEMORY pill in the header;
  MainActivity toggles screens with BackHandler, no new UI dependencies.
  First CI attempt failed (missing material3 Surface import + smart cast on
  delegated state) - fixed in 8c8a4c1; run 31866771457 GREEN.
  On-device verify PENDING (device in use): open MEMORY -> browse session
  from the earlier Test 1 run -> force-stop -> reopen -> history survives.
  section: A
  tags: [memory, ui, screen, m1, compose]
