// Lightweight memory/backend reporting (Phase 1). Allocation-level accounting
// (native allocations, KV-cache estimate per model) arrives with Phase 6
// profiling — nothing here is fabricated.
#pragma once

#include <string>

#include "llama.h"

class MemoryMonitor {
public:
    static std::string statsJson(const llama_model* model, const llama_context* ctx,
                                 int32_t threads, int32_t gpu_layers,
                                 enum ggml_type type_k, enum ggml_type type_v);
    static std::string backendJson(const llama_context* ctx,
                                   int32_t threads, int32_t gpu_layers,
                                   enum ggml_type type_k, enum ggml_type type_v);
};
