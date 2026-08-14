cmake_minimum_required(VERSION 3.22)
project(nativeai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp (submodule, pinned b10428). Baseline mirrors llama.cpp's own
# Android CI (build-android.yml): CPU-only with runtime dispatch.
# Vulkan offload (Adreno 640) is a Phase 7 measured optimization — the spec
# forbids claiming hardware acceleration before verifying it.
set(GGML_VULKAN OFF CACHE BOOL "Vulkan backend (Phase 7)" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "OpenMP" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "no host -march" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "runtime CPU dispatch (spec: detect, don't hardcode)" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "dynamic CPU variant loading (required by ALL_VARIANTS)" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "no examples" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "no tests" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "no tools" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "no server" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "no unified binary" FORCE)
add_subdirectory(
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
    llama.cpp)

# No hardcoded -march/-O3/-flto: GGML_CPU_ALL_VARIANTS dispatches per-CPU.
# A SNAPDRAGON_855 profile may be added in Phase 7 after benchmarking proves
# it (spec rule: never blind-compile flags).

add_library(native-lib SHARED
    native-lib.cpp
    NativeEngine.cpp
    MemoryMonitor.cpp)
target_link_libraries(native-lib PRIVATE llama)
target_include_directories(native-lib PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/ggml/include)
