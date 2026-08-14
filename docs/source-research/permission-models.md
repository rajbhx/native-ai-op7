# Source research — permission models

## Codex / OpenCode model
- Graded approvals: read-only vs mutation; explicit user consent for
  destructive/paid actions; the model cannot bypass checks.

## Adapted model (master prompt §21)
- `READ_ONLY`: file_search, memory_search, system_info, model_info.
- `SAFE`: calculator, web_search (fallback provider, no side effects).
- `REQUIRES_APPROVAL`: file_write (future).
- `PRIVILEGED`: system modification (future).

## Enforcement points
- `ToolExecutor` performs the permission check before execution; the model
  cannot call a tool whose level exceeds the current policy.
- Paid remote models are rejected by the router unless `allowPaid` is set
  (ADR-004).
- Remote requests are filtered by `MemoryPrivacyFilter` and routing mode
  (ADR-007).

## Verdict (ADR-006/ADR-007)
Permission levels are a first-class field of every tool; approval UI lands
with the production UI phase.
