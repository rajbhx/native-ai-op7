# VISION — Memory-first, model-independent, self-learning agent

Source of truth: the project's original master prompt (memory-first, model
independent). The LLM is ONE component, not the product. Intelligence comes
from: persistent memory + internet + free remote models + small local GGUF +
tools + skills + agents + verification + experience + project knowledge.

This file is the alignment map: master-prompt requirement -> current repo
state -> gap -> next action. Status: ✅ shipped / 🟡 partial / ⬜ planned.
Gate for every item: CI green -> on-device verified -> field note.

## Gap table

| # | Master-prompt requirement | Current state | Gap / next action |
|---|---------------------------|---------------|-------------------|
| 1 | Small GGUF = local base, configurable | ✅ `LocalModelProvider`, mmap, downloader, thread/ctx selectors, benchmark (Qwen-1B, ~491 MB, 1.3-1.96 tok/s) | model-replacement flow (swap + re-verify) |
| 2 | Layered project specialization (base + prompt + memory + skills + tools + experiences + optional LoRA) | 🟡 system prompt + memory + skills + tools assembled in `ContextManager`; LoRA gated in `SelfLearningPipeline` | training is docs-only; keep gated, no change until Phase 6 |
| 3 | Internet as first-class intelligence | 🟡 `WebSearchTool` (bounded HTTP, local fallback) | `WebResearchEngine`: decide -> search -> extract -> compare sources -> summarize -> verify -> store |
| 4 | Dynamically discovered free providers | ✅ OpenCode Zen catalog discovery, health monitor, cache + persistence, re-hydration | keep refreshable; never hard-code availability |
| 5 | `ModelRouter` picks best source | ✅ AUTO/FREE/LOCAL/OFFLINE, capability-aware, explicit selection wins | tool-only / memory-only fallback tiers |
| 6 | No single-model lock-in; survive provider loss | ✅ fallback remote -> alternate -> local (max 3 attempts) | same |
| 7 | Persistent memory is the core | 🟡 SQLite + FTS5 (OP7 fallback), experiences/facts/sessions/tool_results + messages (v3, M1a), BM25 + decay + ranking | missing stores: notes, problems, solutions, research, projects, failures; Memory screen (M1b) |
| 8 | Memory update, never blind overwrite | 🟡 `semantic_facts` with confidence/last_verified | old/new knowledge versioning + OUTDATED marking |
| 9 | Internet + memory contradiction detection | ⬜ | part of `WebResearchEngine` |
| 10 | Self-learning loop | 🟡 verified-experience JSONL export + eligibility gate | quality filters, dedupe-by-semantics, dataset versioning |
| 11 | Training-data schema (task/context/memory/tool_action/observation/result/verification) | 🟡 JSONL prompt/completion only | v2 schema, never raw CoT |
| 12 | Modular agents (Research/Coding/Debug/Android/Web/Memory/Build/Diagnostics) | ⬜ single `ThinkingAgent` | `ResearchAgent` first, sharing Memory/Tools/Router/Verifier |
| 13 | External coding-agent adapters | ⬜ docs only (Termux audit, clean-room) | optional adapters; memory stays authoritative |
| 14 | Offline-first | ✅ local model + memory + tools work offline | same |
| 15 | Tool-first design | ✅ calculator/file_search/system_info deterministic; terminal deny-by-default | same |
| 16 | Memory-first answering (search before model) | ✅ `ThinkingAgent` runs `memory.searchContext` before the prompt; remote gets `MemoryPrivacyFilter` | same |
| 17 | Decision pipeline (classify -> memory -> tools -> internet -> model -> execute -> verify -> store) | ✅ state machine + `TaskClassifier` | same |
| 18 | OP7 optimization, measured | ✅ memory audit, perf logs, thread sweep planned (Phase 7) | finish 2-6 thread sweep; decide `-march` only from data |
| 19 | 1.5 GB memory budget | ✅ `MemoryBudget` + `MemoryWatchdog` | same |
| 20 | UX: agent status, memory screen, model hub | 🟡 status + trace exist | Memory screen; Model Hub consolidation (already scoped) |

## First-deliverable proof (master prompt §27)

Scenario: "How did we solve this problem previously?" -> search persistent
memory -> retrieve experience -> if insufficient, search internet -> invoke
local GGUF or free remote -> verify -> store new knowledge -> answer.
Acceptance: force-stop the app (lose all RAM state), reopen, and the agent
continues from disk-persisted memory. `experiences`/FTS5 + `sessions` +
`tool_results` already persist; the missing proof is a restart test on
device (STORE currently did not write an experience row in Test 1 - verify).

## Reshaped build order (memory-first)

- In flight (this revision): tool-result observability (input/error stored,
  `SafeExpr` e-notation + thousands separators).
- In flight (next revision): M1a conversation store — `messages` table (schema v3),
  user prompt + final answer persisted per session; restart-proof via disk.
- Then: Phase A (tools panel + service state + stop/abort), M1b Memory screen,
  M2 knowledge update/OUTDATED, M3 `WebResearchEngine`, M4 `ResearchAgent`,
  M5 training schema v2 + quality gates.
