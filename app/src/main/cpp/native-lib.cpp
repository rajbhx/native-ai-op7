// JNI glue — all engine logic lives in NativeEngine (RAII).
#include <jni.h>

#include <string>
#include <vector>

#include "NativeEngine.hpp"

namespace {

NativeEngine g_engine;

std::string jstring_to_utf8(JNIEnv* env, jstring s) {
    if (s == nullptr) {
        return "";
    }
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c != nullptr ? c : "");
    if (c != nullptr) {
        env->ReleaseStringUTFChars(s, c);
    }
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_NativeEngine_nativeInit(JNIEnv* env, jobject,
                                                 jstring model_path,
                                                 jint threads, jint gpu_layers,
                                                 jint n_ctx,
                                                 jboolean pin_high_cores,
                                                 jstring native_lib_dir) {
    NativeEngine::Config cfg;
    cfg.model_path = jstring_to_utf8(env, model_path);
    cfg.threads = threads > 0 ? threads : 4;
    cfg.gpu_layers = gpu_layers;
    cfg.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    cfg.pin_high_cores = pin_high_cores == JNI_TRUE;
    cfg.native_lib_dir = jstring_to_utf8(env, native_lib_dir);
    return g_engine.init(cfg) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGenerate(JNIEnv* env, jobject,
                                                     jstring prompt,
                                                     jint max_tokens) {
    const std::string json =
        g_engine.generate(jstring_to_utf8(env, prompt), max_tokens);
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGenerateStream(
    JNIEnv* env, jobject, jstring prompt, jint max_tokens, jfloat temperature,
    jfloat top_p, jint top_k, jfloat repeat_penalty, jint penalty_last_n,
    jobjectArray stop_sequences, jobject callback) {
    NativeEngine::GenerationConfig gc;
    gc.max_tokens = max_tokens > 0 ? max_tokens : 128;
    gc.temperature = temperature;
    gc.top_p = top_p;
    gc.top_k = top_k;
    gc.repeat_penalty = repeat_penalty;
    gc.penalty_last_n = penalty_last_n;
    if (stop_sequences != nullptr) {
        const jsize n = env->GetArrayLength(stop_sequences);
        for (jsize i = 0; i < n; ++i) {
            jstring s = static_cast<jstring>(env->GetObjectArrayElement(stop_sequences, i));
            gc.stop_sequences.push_back(jstring_to_utf8(env, s));
            env->DeleteLocalRef(s);
        }
    }

    if (callback == nullptr) {
        return;
    }
    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token =
        env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cb_cls);
    if (on_token == nullptr) {
        return;  // pending NoSuchMethodError propagates to the caller
    }

    const std::string prompt_str = jstring_to_utf8(env, prompt);
    g_engine.generateStream(
        prompt_str, gc,
        [env, callback, on_token](const std::string& piece) {
            jstring js = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, on_token, js);
            env->DeleteLocalRef(js);
        });
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_NativeEngine_nativeCancel(JNIEnv*, jobject) {
    g_engine.cancel();
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGetMemoryStats(JNIEnv* env, jobject) {
    return env->NewStringUTF(g_engine.memoryStatsJson().c_str());
}

JNIEXPORT jlong JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGetRssBytes(JNIEnv*, jobject) {
    return static_cast<jlong>(g_engine.rssBytes());
}

JNIEXPORT jstring JNICALL
Java_com_engine_nativeai_NativeEngine_nativeGetBackendInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF(g_engine.backendInfoJson().c_str());
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_NativeEngine_nativeUnload(JNIEnv*, jobject) {
    g_engine.unload();
}

}  // extern "C"
