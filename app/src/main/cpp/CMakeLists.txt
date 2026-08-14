cmake_minimum_required(VERSION 3.22)
project(nativeai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp (submodule) — Vulkan GPU offload + OpenMP.
set(GGML_VULKAN ON CACHE BOOL "Vulkan backend" FORCE)
set(GGML_OPENMP ON CACHE BOOL "OpenMP" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "no host -march" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "no examples" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "no tests" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "no tools" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "no server" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "no unified binary" FORCE)
add_subdirectory(
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
    llama.cpp)

# Snapdragon 855 (arm64-v8a): Kryo 485 gold/prime core tuning.
# Phase 1 replaces this hardcoded block with capability detection
# (GENERIC_ARM64 / SNAPDRAGON_855 / DEBUG / RELEASE / BENCHMARK profiles).
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+dotprod+fp16 -O3 -flto)
endif()

add_library(native-lib SHARED native-lib.cpp)
target_link_libraries(native-lib PRIVATE llama)
target_include_directories(native-lib PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/ggml/include)
