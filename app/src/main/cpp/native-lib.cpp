// Phase 1 JNI bridge — DRAFT baseline.
// Audited against the pinned llama.cpp submodule commit (b10428). Uses only
// functions that exist in that exact checkout:
//   llama_init_from_model (llama_new_context_with_model is deprecated here),
//   llama_vocab_eos (llama_token_eos is deprecated),
//   llama_memory_clear (llama_kv_cache_clear was renamed),
//   llama_sampler_init_greedy / llama_sampler_sample.
// mmap is automatic in b10428 (llama_model_params has no use_mmap field).
// Streaming (Flow<String>), MemoryMonitor and ModelManager arrive in Phase 1
// proper — see docs/GOLD-STANDARD-SPEC.md.
#include <jni.h>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "llama.h"

namespace {

llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;
std::atomic<bool> g_cancel{false};
int32_t g_threads = 4;
int32_t g_gpu_layers = 0;
int32_t g_n_ctx = 2048;
enum ggml_type g_type_k = GGML_TYPE_Q8_0;
enum ggml_type g_type_v = GGML_TYPE_Q8_0;

const char* kv_type_name(enum ggml_type t) {
  switch (t) {
    case GGML_TYPE_Q8_0: return "Q8_0";
    case GGML_TYPE_F16: return "F16";
    default: return "default";
  }
}

// Minimal JSON escaping for the small payloads this bridge returns.
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

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_NativeEngine_nativeInit(JNIEnv* env, jobject,
                                                 jstring model_path,
                                                 jint threads,
                                                 jint gpu_layers,
                                                 jint n_ctx) {
  const char* path = env->GetStringUTFChars(model_path, nullptr);
  if (path == nullptr) {
    return JNI_FALSE;
  }

  llama_backend_init();

  llama_model_params mparams = llama_model_default_params();
  // Conservative default: no GPU offload until Phase 1 benchmarks prove the
  // right layer count for the Adreno 640 within the 1.5 GB budget.
  mparams.n_gpu_layers = gpu_layers;
  g_model = llama_load_model_from_file(path, mparams);
  env->ReleaseStringUTFChars(model_path, path);
  if (g_model == nullptr) {
    return JNI_FALSE;
  }

  llama_context_params cparams = llama_context_default_params();
  cparams.n_ctx = n_ctx > 0 ? n_ctx : 2048;
  cparams.type_k = g_type_k;  // Q8_0 KV-cache quantization (experimental)
  cparams.type_v = g_type_v;
  cparams.n_threads = threads > 0 ? threads : 4;
  g_ctx = llama_init_from_model(g_model, cparams);
  if (g_ctx == nullptr && g_type_k == GGML_TYPE_Q8_0) {
    // Spec rule: never silently claim an unsupported KV type. Fall back and
    // keep going with the backend defaults.
    cparams.type_k = llama_context_default_params().type_k;
    cparams.type_v = llama_context_default_params().type_v;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx != nullptr) {
      g_type_k = cparams.type_k;
      g_type_v = cparams.type_v;
    }
  }
  if (g_ctx == nullptr) {
    llama_free_model(g_model);
    g_model = nullptr;
    return JNI_FALSE;
  }
  g_threads = cparams.n_threads;
  g_gpu_layers = gpu_layers;
  g_n_ctx = static_cast<int32_t>(llama_n_ctx(g_ctx));
  return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGenerate(JNIEnv* env, jobject,
                                                     jstring prompt,
                                                     jint max_tokens) {
  if (g_model == nullptr || g_ctx == nullptr) {
    return env->NewStringUTF("{\"text\":\"\",\"cancelled\":false,\"tokens\":0}");
  }

  const char* text = env->GetStringUTFChars(prompt, nullptr);
  const std::string prompt_str(text == nullptr ? "" : text);
  if (text != nullptr) {
    env->ReleaseStringUTFChars(prompt, text);
  }

  g_cancel = false;
  llama_memory_clear(g_ctx);  // single-turn baseline; slots come in Phase 1

  const llama_vocab* vocab = llama_model_get_vocab(g_model);
  const llama_token eos = llama_vocab_eos(vocab);

  std::vector<llama_token> prompt_tokens(2048);
  const int32_t n_prompt = llama_tokenize(
      vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
      prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()),
      /*add_special=*/true, /*parse_special=*/false);
  if (n_prompt < 0) {
    return env->NewStringUTF("{\"text\":\"\",\"cancelled\":false,\"tokens\":0}");
  }
  prompt_tokens.resize(n_prompt);

  llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
  if (llama_decode(g_ctx, batch) != 0) {
    return env->NewStringUTF("{\"text\":\"\",\"cancelled\":false,\"tokens\":0}");
  }

  llama_sampler* sampler = llama_sampler_init_greedy();
  std::string out;
  int32_t generated = 0;
  const int32_t limit = max_tokens > 0 ? max_tokens : 128;
  char piece[64];
  while (generated < limit && !g_cancel.load()) {
    const llama_token id = llama_sampler_sample(sampler, g_ctx, -1);
    if (id == eos) {
      break;
    }
    const int32_t n = llama_token_to_piece(vocab, id, piece, sizeof(piece),
                                           /*lstrip=*/0, /*special=*/false);
    if (n > 0) {
      out.append(piece, static_cast<size_t>(n));
    }
    ++generated;
    llama_token one = id;
    llama_batch one_batch = llama_batch_get_one(&one, 1);
    if (llama_decode(g_ctx, one_batch) != 0) {
      break;
    }
  }
  llama_sampler_free(sampler);

  const bool cancelled = g_cancel.load();
  std::string result = "{\"text\":\"" + json_escape(out) + "\",\"cancelled\":" +
                       (cancelled ? "true" : "false") + ",\"tokens\":" +
                       std::to_string(generated) + "}";
  return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_NativeEngine_nativeCancel(JNIEnv*, jobject) {
  g_cancel = true;
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGetMemoryStats(JNIEnv* env, jobject) {
  if (g_model == nullptr || g_ctx == nullptr) {
    return env->NewStringUTF("{}");
  }
  char json[256];
  std::snprintf(json, sizeof(json),
                "{\"model_bytes\":%llu,\"n_ctx\":%d,\"kv_type_k\":\"%s\","
                "\"kv_type_v\":\"%s\",\"threads\":%d,\"gpu_layers\":%d,"
                "\"gpu_offload_supported\":%s}",
                static_cast<unsigned long long>(llama_model_size(g_model)),
                g_n_ctx, kv_type_name(g_type_k), kv_type_name(g_type_v),
                g_threads, g_gpu_layers,
                llama_supports_gpu_offload() ? "true" : "false");
  return env->NewStringUTF(json);
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGetBackendInfo(JNIEnv* env, jobject) {
  if (g_model == nullptr || g_ctx == nullptr) {
    return env->NewStringUTF("{}");
  }
  char json[256];
  std::snprintf(json, sizeof(json),
                "{\"backend\":\"ggml%s\",\"n_ctx\":%d,\"type_k\":\"%s\","
                "\"type_v\":\"%s\",\"threads\":%d,\"gpu_layers\":%d}",
                llama_supports_gpu_offload() ? "+vulkan" : "",
                g_n_ctx, kv_type_name(g_type_k), kv_type_name(g_type_v),
                g_threads, g_gpu_layers);
  return env->NewStringUTF(json);
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_NativeEngine_nativeUnload(JNIEnv*, jobject) {
  g_cancel = true;
  if (g_ctx != nullptr) {
    llama_free(g_ctx);
    g_ctx = nullptr;
  }
  if (g_model != nullptr) {
    llama_free_model(g_model);
    g_model = nullptr;
  }
  llama_backend_free();
  g_threads = 4;
  g_gpu_layers = 0;
  g_n_ctx = 2048;
  g_type_k = GGML_TYPE_Q8_0;
  g_type_v = GGML_TYPE_Q8_0;
}

}  // extern "C"
