# Session digest — 2026-08-14 — first green CI baseline build

## Problems solved
- **P** llama.cpp submodule tag checkout fails in a shallow clone ("origin/b10428 is not a commit")
  cause: `--depth 1 --branch <tag>` fetches only the default branch, so release tags are absent
  solution: submodule add shallow, then `fetch --depth 1 origin tag b10428` + checkout + `git add` the gitlink
  section: A
  tags: [submodule, llama.cpp, git]
- **P** "E: Unable to locate package shaderc" on the Ubuntu 24.04 runner
  cause: shaderc was restructured in noble; llama.cpp's own CI uses glslc + spirv-headers
  solution: `apt-get install -y glslc libvulkan-dev spirv-headers`
  section: A
  tags: [vulkan, github-actions, llama.cpp]
- **P** "GGML_CPU_ALL_VARIANTS requires GGML_BACKEND_DL" at CMake configure
  cause: llama.cpp gates ALL_VARIANTS behind dynamic backend loading (ggml/src/CMakeLists.txt:373)
  solution: set GGML_BACKEND_DL=ON together with GGML_CPU_ALL_VARIANTS=ON (mirrors llama.cpp build-android.yml)
  section: A
  tags: [cmake, llama.cpp, github-actions]
- **P** "Inconsistent JVM-target compatibility (1.8 vs 17)" fails compileDebugKotlin
  cause: AGP defaults Java target 1.8 while Kotlin jvmTarget defaulted to 17
  solution: compileOptions VERSION_17 + kotlinOptions { jvmTarget = "17" } inside android {}
  section: A
  tags: [gradle, kotlin, jvm]
- **P** "Unresolved reference ... BaseAppModuleExtension.kotlinOptions" in build.gradle.kts
  cause: kotlinOptions used at top level instead of scoped inside the android {} extension
  solution: move the block inside android {}
  section: A
  tags: [gradle, kotlin]
- **P** "no matching function for call to 'llama_memory_clear'" at b10428
  cause: KV-cache API refactor — memory ops take a llama_memory_t handle, not the context
  solution: `llama_memory_clear(llama_get_memory(g_ctx), false)` with null guard
  section: A
  tags: [llama.cpp, api, kv-cache]

## Notes (optional)
- Baseline CI config mirrors llama.cpp's own build-android.yml: GGML_VULKAN=OFF,
  GGML_OPENMP=OFF, GGML_NATIVE=OFF, GGML_CPU_ALL_VARIANTS=ON, GGML_BACKEND_DL=ON.
  Vulkan (Adreno 640) is a Phase 7 measured optimization, not a baseline claim.
- Added aapt badging validation gate (package id, minSdk 29, targetSdk 34,
  native-code arm64-v8a, no testOnly) before artifact upload.
- Run 31799919311 green; artifact native-ai-op7-debug (7.5 MB).
