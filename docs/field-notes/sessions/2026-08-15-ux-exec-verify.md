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
