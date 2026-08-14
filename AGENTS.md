# AGENTS.md — native-ai-op7

Bespoke native engine (not a fork of an app). Builds happen on GitHub
Actions only — never build/compile locally. llama.cpp is the ONLY upstream
dependency; changes to it live in `patches/op7/` (thin layer), never a fork.
llama.cpp is pinned (submodule `b10428`): audit the checked-out API before
writing any native code — never use obsolete functions.

Before doing anything: load the `op7-special-build` skill and search the
playbook first (`python3 scripts/lookup.py <problem words>` in a playbook
clone). Record every solved problem in `docs/field-notes/` (session digest ->
`log.yml`), same loop as iceraven-op7.

Hard constraints: 1.5 GB max AI-context RAM; 4 threads pinned to Kryo 485
Gold/Prime; mmap GGUF weights; Q8_0 KV-cache; SQLite BM25/FTS5 fast memory ->
verified tool execution -> synthetic LoRA dataset -> finetune.

Read `docs/GOLD-STANDARD-SPEC.md` first — it overrides any older phase prompt.
