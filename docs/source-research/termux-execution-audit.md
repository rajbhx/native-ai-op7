# Termux execution-layer audit (2026-08-15)

Reference: `termux/termux-app` (shallow, blob-none sparse checkout at
`b10428`-era main; app module only). Goal: borrow architecture ideas for a
replaceable execution layer — never fork, copy, or embed Termux.

## Current architecture (native-ai-op7)

- Model layer: `ModelProvider`/`ModelDescriptor`/`ModelRegistry` +
  `ModelRouter` (runtime-dispatched CPUs; remote via OpenAI-compatible
  providers). No inference-backend abstraction yet (Vulkan/NNAPI = Phase 7).
- Agent: `ThinkingAgent` state machine over `AgentEvent`; tools behind
  `AgentTool` (`name`, `description`, `permission`, `execute`) in
  `ToolRegistry`, executed by bounded `ToolExecutor` (timeout, input/output
  limits, `PermissionManager` with `ToolPermission` levels).
- Persistence: SQLite (`MemoryDatabase`, FTS5 w/ fallback) + JSON catalog.
- UI: Compose, one `EngineScreen` state machine (`EngineUiState`), bottom
  sheet settings, agent trace.
- Background: `EngineForegroundService` holds engine + memory.

## Termux components vs our needs

| Termux component | Purpose | Reuse | Adapt | Avoid | License note |
| --- | --- | --- | --- | --- | --- |
| `TermuxService` | Foreground service owning `TermuxSession`s | no | idea only | forking its Binder/session plumbing | GPL-3.0 |
| `RunCommandService` | Third-party `ExecutionCommand` (executable/args/timeout, exit callback) | no | clean-room `ExecutionRequest`/`ExecutionResult` | copying intent IPC surface | GPL-3.0 |
| `TermuxActivity` + terminal views | Emulator UI | no | no | embedding jackpal emulator now | GPL-3.0 (emulator lib Apache-2.0) |
| Termux bootstrap/package env | `pkg install` Linux toolchain | no | optional future `TermuxBackend` | bundling a distro into the APK | GPL-3.0 |
| `termux-shared` utilities | Files/logging/errors | no | clean-room only | wholesale import | GPL-3.0 |
| `TermuxOpenReceiver`/intents | External command entry | no | future `RemoteExecutionBackend` | — | GPL-3.0 |

Conclusion: the whole upstream repo is **GPL-3.0-only** (terminal-emulator
libs are Apache-2.0). This project is a bespoke native engine with no GPL
intent, so nothing is copied; interfaces are drawn in spirit only.

## Implemented increment (smallest safe step)

- `ExecutionBackend` (interface) + `LocalProcessBackend` (short-lived
  `ProcessBuilder`, hard timeout, cancel teardown, structured result).
- `ExecutionPolicy` (allow-list; default **deny all**).
- `TerminalTool` (`AgentTool`, `REQUIRES_APPROVAL`, disabled by default;
  availability surfaces honestly to the agent prompt).
- `AgentTool.available` + `ToolRegistry.descriptions()` filter so the model
  is never advertised capabilities that are off.

## Track E — Termux backend at core (implemented)

- `TermuxBackend` (clean-room, `ExecutionBackend`): detects `com.termux`
  (PackageManager + `<queries>`), probes readiness (`echo termux-ok` via the
  public `com.termux.RUN_COMMAND` RunCommandService intent), and executes one
  bounded command per call. Output is captured by file exchange under
  `/sdcard/Download/nativeai/<runId>/` (out/err/code), with hard timeout,
  cancellation, best-effort `pkill` via an argv run tag, and cleanup.
- `TermuxStatus`: NOT_INSTALLED / INSTALLED / SETUP_REQUIRED / READY / ERROR
  with human reasons — the UI never claims Termux capability it cannot show.
- `ExecutionManager`: picks Termux when READY, else `LocalProcessBackend`
  (AI engine works with neither Termux installed nor a local model).
- One-time user setup surfaced in the Tools settings row: install Termux,
  run `termux-setup-storage` once, enable “Allow external apps”.
- Storage: `READ_EXTERNAL_STORAGE` (maxSdk 32) + MediaStore fallback path for
  future API 33+ devices; no bundled runtime, no GPL code, no new deps.

## Not done (by design, next phases)

- Persistent terminal sessions, TTY, streaming, renderable terminal UI.
- `FutureContainerBackend`, `RemoteExecutionBackend`.
- Tools panel polish; system-prompt editing; chat history.
- `InferenceBackend` abstraction (Vulkan/NNAPI) — measured Phase 7 work.

## Memory / resource note

`LocalProcessBackend` spawns one short-lived process per call; no daemons,
no persistent sessions, per-call teardown. It does not change APK size or
baseline RAM. Any future Termux-based backend stays optional and out-of-APK.
