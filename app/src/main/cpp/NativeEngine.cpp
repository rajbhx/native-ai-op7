#include "NativeEngine.hpp"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <unistd.h>

#include "hardware_detector.hpp"
#include "MemoryMonitor.hpp"

namespace {

std::string json_escape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c;
        }
    }
    return out;
}

bool ends_with(const std::string& s, const std::string& suffix) {
    return s.size() >= suffix.size() &&
           s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

void log_to_logcat(ggml_log_level level, const char* text, void* /*user_data*/) {
    if (text == nullptr) {
        return;
    }
    const int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
                     : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN
                     : level == GGML_LOG_LEVEL_DEBUG ? ANDROID_LOG_DEBUG
                                                     : ANDROID_LOG_INFO;
    __android_log_write(prio, "llama", text);
}

}  // namespace

NativeEngine::~NativeEngine() {
    unload();
}

bool NativeEngine::init(const Config& config) {
    if (model_ != nullptr || ctx_ != nullptr) {
        return false;
    }
    config_ = config;
    cancel_.store(false);

    if (!config_.native_lib_dir.empty()) {
        // GGML_BACKEND_DL searches the executable dir + cwd, neither of which
        // is the APK lib dir on Android; load the CPU backend variants from
        // the app's nativeLibraryDir explicitly before backend init.
        ggml_backend_load_all_from_path(config_.native_lib_dir.c_str());
    }
    llama_log_set(log_to_logcat, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = config_.gpu_layers;
    model_ = llama_model_load_from_file(config_.model_path.c_str(), mparams);
    if (model_ == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "NativeEngineJNI",
                            "nativeInit failed: model load returned null (%s)",
                            config_.model_path.c_str());
        return false;
    }
    vocab_ = llama_model_get_vocab(model_);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = config_.n_ctx;
    cparams.type_k = type_k_;
    cparams.type_v = type_v_;
    cparams.n_threads = config_.threads;
    ctx_ = llama_init_from_model(model_, cparams);
    if (ctx_ == nullptr && type_k_ == GGML_TYPE_Q8_0) {
        // Spec rule: never silently claim an unsupported KV type. Fall back
        // to backend defaults and keep going.
        __android_log_print(ANDROID_LOG_WARN, "NativeEngineJNI",
                            "Q8_0 KV cache rejected by this model; falling back to backend defaults");
        cparams.type_k = llama_context_default_params().type_k;
        cparams.type_v = llama_context_default_params().type_v;
        ctx_ = llama_init_from_model(model_, cparams);
        if (ctx_ != nullptr) {
            type_k_ = cparams.type_k;
            type_v_ = cparams.type_v;
        }
    }
    if (ctx_ == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "NativeEngineJNI",
                            "nativeInit failed: context create returned null (n_ctx=%d, threads=%d)",
                            cparams.n_ctx, cparams.n_threads);
        llama_model_free(model_);
        model_ = nullptr;
        vocab_ = nullptr;
        return false;
    }
    config_.threads = cparams.n_threads;

    // Blueprint Phase 2: pin this thread (the one that drives llama_decode)
    // to the high Gold/Prime cores BEFORE first graph compute, so the worker
    // threads llama spawns inherit the same affinity. Honest + measurable;
    // affinity is never applied blindly (ADR-009).
    if (config_.pin_high_cores) {
        const std::vector<int> cores = hw::highCores();
        if (!cores.empty() && static_cast<int>(cores.size()) >= config_.threads) {
            const bool pinned = hw::pinCurrentThread(cores);
            __android_log_print(
                ANDROID_LOG_INFO, "NativeEngineJNI",
                pinned ? "Successfully pinned execution thread %ld to Kryo 485 Gold/Prime cores (%d cores)"
                       : "Thread pinning unavailable on this device (falling back to default affinity)",
                static_cast<long>(gettid()), static_cast<int>(cores.size()));
        }
    }
    __android_log_print(ANDROID_LOG_INFO, "NativeEngineJNI",
                        "Native Engine Initialized Successfully (n_ctx=%d, n_threads=%d)",
                        llama_n_ctx(ctx_), static_cast<int>(config_.threads));
    return true;
}

void NativeEngine::runGeneration(const std::string& prompt, int32_t max_tokens,
                                 llama_sampler* sampler,
                                 const std::vector<std::string>& stops,
                                 const TokenCallback* cb, std::string& out,
                                 int32_t& generated_out) {
    cancel_.store(false);
    out.clear();
    generated_out = 0;

    if (llama_memory_t mem = llama_get_memory(ctx_); mem != nullptr) {
        llama_memory_clear(mem, /*data=*/false);  // single-turn baseline
    }

    std::vector<llama_token> prompt_tokens(2048);
    const int32_t n_prompt = llama_tokenize(
        vocab_, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
        /*add_special=*/true, /*parse_special=*/false);
    if (n_prompt < 0) {
        return;
    }
    prompt_tokens.resize(n_prompt);

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
    if (llama_decode(ctx_, batch) != 0) {
        return;
    }

    const llama_token eos = llama_vocab_eos(vocab_);
    const int32_t limit = max_tokens > 0 ? max_tokens : 128;
    char piece[64];
    int32_t generated = 0;
    while (generated < limit && !cancel_.load()) {
        const llama_token id = llama_sampler_sample(sampler, ctx_, -1);
        if (id == eos) {
            break;
        }
        const int32_t n = llama_token_to_piece(vocab_, id, piece, sizeof(piece),
                                               /*lstrip=*/0, /*special=*/false);
        if (n > 0) {
            out.append(piece, static_cast<size_t>(n));
            if (cb != nullptr) {
                (*cb)(std::string(piece, static_cast<size_t>(n)));
            }
        }
        ++generated;

        bool stopped = false;
        for (const std::string& stop : stops) {
            if (!stop.empty() && ends_with(out, stop)) {
                out.erase(out.size() - stop.size());
                stopped = true;
                break;
            }
        }
        if (stopped) {
            break;
        }

        llama_token one = id;
        llama_batch one_batch = llama_batch_get_one(&one, 1);
        if (llama_decode(ctx_, one_batch) != 0) {
            break;
        }
    }
    generated_out = generated;
}

std::string NativeEngine::generate(const std::string& prompt, int32_t max_tokens) {
    if (!loaded()) {
        return "{\"text\":\"\",\"cancelled\":false,\"tokens\":0}";
    }
    llama_sampler* sampler = llama_sampler_init_greedy();
    std::string out;
    int32_t generated = 0;
    const std::vector<std::string> no_stops;
    runGeneration(prompt, max_tokens, sampler, no_stops, nullptr, out, generated);
    llama_sampler_free(sampler);

    const bool cancelled = cancel_.load();
    std::string json = "{\"text\":\"" + json_escape(out) + "\",\"cancelled\":" +
                       (cancelled ? "true" : "false") + ",\"tokens\":" +
                       std::to_string(generated) + "}";
    return json;
}

void NativeEngine::generateStream(const std::string& prompt,
                                  const GenerationConfig& gc,
                                  const TokenCallback& cb) {
    if (!loaded()) {
        return;
    }
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sparams);
    if (gc.repeat_penalty != 1.0f || gc.penalty_last_n > 0) {
        llama_sampler_chain_add(
            chain, llama_sampler_init_penalties(
                       llama_vocab_n_tokens(vocab_), gc.penalty_last_n,
                       gc.repeat_penalty, 0.0f, 0.0f));
    }
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(gc.top_k));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(gc.top_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(gc.temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    int32_t generated = 0;
    runGeneration(prompt, gc.max_tokens, chain, gc.stop_sequences, &cb, out,
                  generated);
    llama_sampler_free(chain);
}

std::string NativeEngine::memoryStatsJson() const {
    return MemoryMonitor::statsJson(model_, ctx_, config_.threads,
                                    config_.gpu_layers, type_k_, type_v_);
}

std::string NativeEngine::backendInfoJson() const {
    return MemoryMonitor::backendJson(ctx_, config_.threads,
                                      config_.gpu_layers, type_k_, type_v_);
}

uint64_t NativeEngine::rssBytes() const {
    return hw::rssBytes();
}

void NativeEngine::unload() {
    cancel_.store(true);
    if (ctx_ != nullptr) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_ != nullptr) {
        llama_model_free(model_);
        model_ = nullptr;
    }
    vocab_ = nullptr;
    llama_backend_free();
    type_k_ = GGML_TYPE_Q8_0;
    type_v_ = GGML_TYPE_Q8_0;
}
