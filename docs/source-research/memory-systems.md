# Source research — memory systems (SQLite memory, Engram, MemOS)

## SQLite-based agent memory (SQLite Memory)
- **Problem**: durable, queryable agent memory with zero extra infra.
- **Learn**: FTS5 full-text retrieval; structured tables; cheap persistence.
- **Do NOT copy**: naive "store everything" tables — ours prunes/decays.
- **Android**: yes — SQLite is built in. **OP7 weight**: minimal (5-10 MB).
- **Rewrite natively**: `MemoryDatabase.kt` (experiences FTS5 + semantic_facts
  + tool_results + memory_scores + sessions).

## Engram
- **Problem**: memory with similarity + provenance for coding agents.
- **Learn**: provenance (which file/tool produced a fact), recency + utility
  scoring, retrieval that explains itself.
- **Do NOT copy**: its specific schema/CLI.
- **Android**: concepts only.
- **Rewrite natively**: provenance lives in `tool_results`; utility/recency
  scoring lives in `memory_scores`; retrieval explains via the context block
  (`searchContext`).

## MemOS
- **Problem**: operating-system-style memory abstraction for agents
  (declarative memory APIs).
- **Learn**: separation of declarative memory vs procedural skills.
- **Do NOT copy**: the full API surface.
- **Android**: concepts only.
- **Rewrite natively**: declarative facts (`semantic_facts`) vs skills
  (future `SkillManager`) already split.

## Verdict (ADR-003/ADR-007)
Raw SQLite + FTS5, hybrid ranking, aggressive pruning, privacy filter before
remote use. No vector DB dependency on-device (optional later, measured).
