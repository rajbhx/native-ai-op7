#include "MemoryMonitor.hpp"

#include <cstdio>

#include "hardware_detector.hpp"

namespace {

const char* kv_type_name(enum ggml_type t) {
    switch (t) {
        case GGML_TYPE_Q8_0: return "Q8_0";
        case GGML_TYPE_F16: return "F16";
        default: return "default";
    }
}

}  // namespace

std::string MemoryMonitor::statsJson(const llama_model* model,
                                     const llama_context* ctx,
                                     int32_t threads, int32_t gpu_layers,
                                     enum ggml_type type_k,
                                     enum ggml_type type_v,
                                     bool affinity_applied) {
    if (model == nullptr || ctx == nullptr) {
        return "{}";
    }
    const uint64_t rss = hw::rssBytes();
    const uint64_t limit = 1572864000ULL;  // 1.5 GB AI runtime ceiling
    char buf[420];
    std::snprintf(buf, sizeof(buf),
                  "{\"model_bytes\":%llu,\"n_ctx\":%u,\"kv_type_k\":\"%s\","
                  "\"kv_type_v\":\"%s\",\"threads\":%d,\"gpu_layers\":%d,"
                  "\"gpu_offload_supported\":%s,\"rss_bytes\":%llu,"
                  "\"rss_limit_bytes\":%llu,\"rss_over_limit\":%s,"
                  "\"affinity_applied\":%s}",
                  static_cast<unsigned long long>(llama_model_size(model)),
                  llama_n_ctx(ctx), kv_type_name(type_k), kv_type_name(type_v),
                  threads, gpu_layers,
                  llama_supports_gpu_offload() ? "true" : "false",
                  static_cast<unsigned long long>(rss),
                  static_cast<unsigned long long>(limit),
                  rss > limit ? "true" : "false",
                  affinity_applied ? "true" : "false");
    return buf;
}

std::string MemoryMonitor::backendJson(const llama_context* ctx,
                                       int32_t threads, int32_t gpu_layers,
                                       enum ggml_type type_k,
                                       enum ggml_type type_v) {
    if (ctx == nullptr) {
        return "{}";
    }
    char buf[320];
    std::snprintf(buf, sizeof(buf),
                  "{\"backend\":\"ggml%s\",\"n_ctx\":%u,\"type_k\":\"%s\","
                  "\"type_v\":\"%s\",\"threads\":%d,\"gpu_layers\":%d}",
                  llama_supports_gpu_offload() ? "+vulkan" : "",
                  llama_n_ctx(ctx), kv_type_name(type_k), kv_type_name(type_v),
                  threads, gpu_layers);
    return buf;
}
