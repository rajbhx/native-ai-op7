// Lightweight memory/backend reporting (Phase 1). Allocation-level accounting
// (native allocations, KV-cache estimate per model) arrives with Phase 6
// profiling — nothing here is fabricated.
#pragma once

#include <cstdint>
#include <string>

#include "llama.h"

// Single source of truth for the AI-runtime RAM ceiling: 1536 MiB = 1.5 GB.
// MUST equal Op7SystemProfile.MEMORY_LIMIT_BYTES (Kotlin); the contract is
// pinned by MemoryBudgetTest.nativeCeilingMatchesKotlinSingleSourceOfTruth.
constexpr uint64_t kOp7MemoryLimitBytes = 1610612736ULL;

class MemoryMonitor {
public:
    static std::string statsJson(const llama_model* model, const llama_context* ctx,
                                 int32_t threads, int32_t gpu_layers,
                                 enum ggml_type type_k, enum ggml_type type_v,
                                 bool affinity_applied);
    static std::string backendJson(const llama_context* ctx,
                                   int32_t threads, int32_t gpu_layers,
                                   enum ggml_type type_k, enum ggml_type type_v);
};
