# Source research — tool systems

Cross-project synthesis (OpenCode, OpenClaw, smolagents, OpenHands, Aider,
LangGraph):

## Common winning concepts
1. Tool registry with name/description/schema (discoverable by the model).
2. Uniform execution envelope: input validation -> timeout -> output limit ->
   error handling -> logging -> permission check.
3. Provider-neutral tool-call format (convert provider-specific formats).
4. Every tool returns structured success/error so the verifier can check.

## What is too heavy for the OP7
- Container/sandboxed tool execution (OpenHands Docker) — no.
- LSP-backed workspace tools (OpenCode) — no.
- Long-running daemon tool servers — no; tools run in-process with coroutine
  timeouts.

## Adapted design (ADR-005)
- `AgentTool` interface: `name`, `description`, `suspend execute(input)`.
- `ToolRegistry`: registration + model-facing descriptions.
- `ToolExecutor`: timeout (15 s), input cap (500 chars), output cap (2000),
  cancellation, logging to `tool_results`, permission levels
  (SAFE/READ_ONLY/REQUIRES_APPROVAL/PRIVILEGED — enforcement added with the
  permission system).
- Tools: memory_search, web_search (provider abstraction + local fallback),
  calculator (SafeExpr), file_search (app-private storage only), system_info,
  model_info, final_answer.
