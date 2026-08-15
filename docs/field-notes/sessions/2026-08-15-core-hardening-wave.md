# Session digest — 2026-08-15 — Core-hardening wave (C1–C5)

## Problems solved
- **P** tools were invisible and uncontrolled; only the terminal row existed.
  cause: no inventory contract between ToolRegistry and the UI.
  solution: `ToolDescriptor` + `ToolRegistry.snapshot()` + shared `Toolbox`
  (one construction path for agent and settings); Tools section lists every
  tool with AVAILABLE/DISABLED/APPROVAL/PRIVILEGED badges.
  section: A
  tags: [tools, ui, registry, inventory]
- **P** the agent trace could not prove which execution backend ran a
  terminal command.
  cause: TerminalTool output had no backend marker.
  solution: `AgentTool.backendLabel` (termux/local); TerminalTool prefixes
  observations with `[termux]` / `[local]`.
  section: A
  tags: [trace, termux, terminal, observability]
- **P** the model card had no single authoritative status line; quota state
  was static until a manual Check health press.
  cause: no derived status + no auto health refresh.
  solution: `ModelStatus.line()` (READY/AVAILABLE/NO MODEL FILE/ONLINE/
  OFFLINE derived from real state); auto `provider.health()` once per 60s for
  the active remote model; GGUF quant tag parsed from filename.
  section: A
  tags: [model, state, health, ui]
- **P** no way to set persona/rules without editing code; Stats could not
  show effective affinity or the dynamic memory budget.
  cause: system prompt hardcoded in ThinkingAgent; affinity pin result only
  logged in native code.
  solution: persisted `systemPromptOverride` (prefs -> ThinkingAgent ->
  quick-send, blank = default); `affinity_applied` added to MemoryMonitor
  stats JSON + MemoryStats; Stats shows affinity + budget/max-ctx lines.
  section: A
  tags: [system-prompt, stats, affinity, native, memory]
