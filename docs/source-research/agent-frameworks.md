# Source research — agent frameworks (OpenCode, OpenClaw, Codex, Hermes, AgentScope, smolagents, OpenHands, LangGraph, Aider)

All are reference implementations only (master prompt §26: "the architecture
belongs to this project"). We extract concepts; we copy nothing wholesale.

## OpenCode / Codex (terminal coding agents)
- **Problem**: turn an LLM into a safe, iterative coding agent in a
  workspace.
- **Learn**: agent state machine (plan -> act -> observe); tool-call
  permissions/approval boundaries; session + context management; subagents;
  error recovery; verification loops.
- **Do NOT copy**: the full desktop runtime, plugin ecosystems, LSP servers —
  all far too heavy for a phone.
- **Android**: no (desktop CLI). **OP7 weight**: n/a.
- **Rewrite natively**: the permission model (§21 of the master prompt) is
  adapted into `ToolPermission` levels in our `ToolExecutor`.

## OpenClaw (persistent personal agent)
- **Problem**: persistent, always-on personal agent with skills and channels.
- **Learn**: gateway/control-plane architecture; skills; sessions; workspace;
  background operation; tool routing; channel abstraction.
- **Do NOT copy**: server infrastructure, channel connectors.
- **Android**: partial (concepts only). **OP7 weight**: too heavy as-is.
- **Rewrite natively**: Android UI -> Agent Gateway -> Agent Kernel -> Native
  Core layering (our architecture).

## Hermes Agent / Hermes WebUI
- **Problem**: agent UI with streaming event visibility.
- **Learn**: agent gateway; streaming agent events (TASK -> PLAN -> TOOL ->
  OBSERVATION -> VERIFY -> FINAL); self-improving skills; tool execution.
- **Do NOT copy**: web UI stack; our UI is native Android XML.
- **Android**: concepts only. **OP7 weight**: n/a.
- **Rewrite natively**: our `AgentEvent` stream (Token/Routed/ToolCall/
  Observation/Verification/Final/Error) is the same idea, native.

## AgentScope / smolagents (lightweight orchestration)
- **Problem**: minimal multi-agent orchestration with tool registration.
- **Learn**: ReAct; simple state machines; explicit interfaces over huge
  frameworks; tool registration pattern (`ToolRegistry`).
- **Do NOT copy**: Python runtime, subagent abstractions we don't need.
- **Android**: concepts only (Python — too heavy to embed).
- **Rewrite natively**: `ToolRegistry`/`ToolExecutor` already mirror this.

## OpenHands (software engineering agent)
- **Problem**: long-horizon software tasks with execution sandboxes.
- **Learn**: planning granularity, observation loop, verification.
- **Do NOT copy**: Docker sandboxing (not phone-appropriate); the scale.
- **Android**: no. **OP7 weight**: n/a.
- **Rewrite natively**: planner concepts feed a future `Planner` step.

## LangGraph
- **Problem**: explicit graph state machines for agents.
- **Learn**: nodes/edges/state; loop control (PLAN -> EXECUTE -> OBSERVE ->
  VERIFY -> REPLAN); iteration/resource limits.
- **Do NOT copy**: Python graph runtime.
- **Android**: no. **OP7 weight**: n/a.
- **Rewrite natively**: our `ThinkingAgent` loop + `AgentState` is a compiled
  version of the same graph.

## Aider
- **Problem**: pairing-based code editing with repo awareness.
- **Learn**: repository map / context budgeting; edit verification.
- **Do NOT copy**: git-integrated editing loop (out of scope for v1).
- **Android**: no. **OP7 weight**: n/a.
- **Rewrite natively**: context budgeting concepts live in `ContextManager` +
  `ContextAdapter`.

## Verdict (ADR-005)
One ReAct loop + strict structured actions + explicit state machine; no
framework dependency.
