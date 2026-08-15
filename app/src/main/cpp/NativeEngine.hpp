// Phase 1 native inference core — llama.cpp b10428.
// Owns model + context with deterministic RAII cleanup.
#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <vector>

#include "llama.h"

class NativeEngine {
public:
    struct Config {
        std::string model_path;
        int32_t threads = 4;
        int32_t gpu_layers = 0;
        int32_t n_ctx = 2048;
        // App nativeLibraryDir; on Android the CPU backend .so lives there
        // (dlopen search paths do not include it by default).
        std::string native_lib_dir;
        // Blueprint Phase 2: pin llama worker threads to the high (Gold/Prime)
        // cores. Measured on-device before any default is trusted (ADR-009).
        bool pin_high_cores = true;
    };

    struct GenerationConfig {
        int32_t max_tokens = 128;
        float temperature = 0.8f;
        float top_p = 0.95f;
        int32_t top_k = 40;
        float repeat_penalty = 1.0f;   // 1.0 = disabled
        int32_t penalty_last_n = 64;   // 0 = disabled
        std::vector<std::string> stop_sequences;
    };

    using TokenCallback = std::function<void(const std::string& piece)>;

    NativeEngine() = default;
    ~NativeEngine();

    NativeEngine(const NativeEngine&) = delete;
    NativeEngine& operator=(const NativeEngine&) = delete;

    bool init(const Config& config);

    // Returns a JSON payload: {"text":...,"cancelled":...,"tokens":...}
    std::string generate(const std::string& prompt, int32_t max_tokens);

    // Streams tokens via callback (greedy-free sampler chain, stop sequences).
    void generateStream(const std::string& prompt, const GenerationConfig& gc,
                        const TokenCallback& cb);

    void cancel() { cancel_.store(true); }
    std::string memoryStatsJson() const;
    std::string backendInfoJson() const;
    void unload();

    bool loaded() const { return model_ != nullptr && ctx_ != nullptr; }
    uint64_t rssBytes() const;

private:
    void runGeneration(const std::string& prompt, int32_t max_tokens,
                       llama_sampler* sampler,
                       const std::vector<std::string>& stops,
                       const TokenCallback* cb, std::string& out,
                       int32_t& generated_out);

    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    const llama_vocab* vocab_ = nullptr;
    Config config_;
    enum ggml_type type_k_ = GGML_TYPE_Q8_0;
    enum ggml_type type_v_ = GGML_TYPE_Q8_0;
    std::atomic<bool> cancel_{false};
    bool affinity_pinned_ = false;
};
