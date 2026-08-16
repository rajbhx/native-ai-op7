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

## Agent-facing conventions (native-ai-op7)
- One authoritative state owner: `EngineViewModel` + StateFlows. UI renders,
  VM decides. Agent/generate loops run on `Dispatchers.Default`, never Main.
- Model load integrity: `ModelManifest` SHA-256 must match before
  `engine.init` — never load a corrupt GGUF; record manifests on download
  and import.
- Router health: use the VM-shared `ProviderHealthMonitor` (failures +
  measured latency). Never create a per-run router that resets health.
- Embeddings: hybrid BM25+HNSW is wired but gated (`docs/EMBEDDINGS.md`).
  Never expose a vector capability without lib probe + model asset +
  on-device benchmark gate. Do NOT implement a hash/keyword "embedding".
- Multi-turn: inject prior conversation via `ThinkingAgent.run(priorConversation=...)`
  and source citations via `AgentEvent.Final.sources` — never inline raw
  memory DB contents.
- Skills: `Toolbox.skillManager` is the single registry; wire new skills
  there, not into the agent directly.
- Memory lifecycle: prune low-utility memories after completed runs
  (best-effort, never fail the run).
- Measure before claiming: benchmark results in Diagnostics come from
  `ModelBenchmark` (real values), never fabricated numbers.
