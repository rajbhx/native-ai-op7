# Session digest — 2026-08-15 — Local GGUF model library

## Problems solved
- **P** the app hardcoded one local model path (`filesDir/models/model.gguf`)
  in nine EngineScreen call sites, so users could not install or switch
  multiple GGUF files.
  cause: `MainActivity` registered a single `LocalModelProvider` and the UI
  built `modelFile` from a fixed path; `LocalModelProvider` also hardcoded
  descriptor id `local-llama`, so per-file providers would have collided.
  solution: `LocalModelLibrary` (pure JVM) scans `models/*.gguf` with stable
  ids (`model.gguf` → `local-llama`, others → `local-<stem>`); per-file
  `LocalModelProvider` sharing the one `NativeEngine`, descriptor override
  param added; `syncInto` drops deleted files and keeps a `local-llama`
  placeholder when the library is empty; `LocalModelImporter` (SAF) copies
  with progress, StatFs +64 MB margin, GGUF magic guard, temp rename;
  `RemoteProviderBootstrap` skips all `local-*` ids on restart; picker gets
  "＋ Pick GGUF from storage…" + size/quant subtitles; model card Delete;
  `loadedPath` guard so switching models actually reloads.
  section: A
  tags: [models, local, gguf, saf, multi-model]
