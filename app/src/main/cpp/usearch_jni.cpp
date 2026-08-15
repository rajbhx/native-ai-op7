// USearch vector index JNI (S7): bounded HNSW insert/search/persist.
// Built into the dedicated vector-lib so the llama native-lib stays
// untouched; loaded lazily by USearchVectorIndex (honest availability).
#include <jni.h>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include <usearch/index_dense.hpp>

namespace {

using namespace unum::usearch;

using vector_index_t = index_dense_gt<>;

vector_index_t* toIndex(jlong h) {
    return reinterpret_cast<vector_index_t*>(h);
}

std::mutex g_mutex;

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeCreate(JNIEnv* env, jclass, jint dims) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dims <= 0 || dims > 4096) return 0;
    try {
        vector_index_t index = vector_index_t::make(
            metric_punned_t(static_cast<std::size_t>(dims),
                            metric_kind_t::cos_k, scalar_kind_t::f32_k));
        if (!index) return 0;
        vector_index_t* ptr = new vector_index_t(std::move(index));
        ptr->reserve(1024);
        return reinterpret_cast<jlong>(ptr);
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    delete toIndex(handle);
}

JNIEXPORT jlong JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeSize(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    vector_index_t* idx = toIndex(handle);
    return idx ? static_cast<jlong>(idx->size()) : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeAdd(
    JNIEnv* env, jclass, jlong handle, jlong key, jfloatArray vector) {
    std::lock_guard<std::mutex> lock(g_mutex);
    vector_index_t* idx = toIndex(handle);
    if (!idx) return JNI_FALSE;
    jsize n = env->GetArrayLength(vector);
    jfloat* data = env->GetFloatArrayElements(vector, nullptr);
    if (!data) return JNI_FALSE;
    bool ok = static_cast<bool>(idx->add(static_cast<vector_index_t::key_t>(key), data));
    env->ReleaseFloatArrayElements(vector, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeSearch(
    JNIEnv* env, jclass, jlong handle, jfloatArray vector, jint k) {
    std::lock_guard<std::mutex> lock(g_mutex);
    vector_index_t* idx = toIndex(handle);
    if (!idx) return nullptr;
    jsize n = env->GetArrayLength(vector);
    jfloat* data = env->GetFloatArrayElements(vector, nullptr);
    if (!data) return nullptr;

    jobjectArray out = nullptr;
    try {
        vector_index_t::search_result_t matches = idx->search(data, static_cast<std::size_t>(k));
        env->ReleaseFloatArrayElements(vector, data, JNI_ABORT);
        if (!matches) return nullptr;

        jclass hitClass = env->FindClass("com/engine/nativeai/VectorHit");
        if (!hitClass) return nullptr;
        jmethodID ctor = env->GetMethodID(hitClass, "<init>", "(JF)V");
        if (!ctor) return nullptr;

        jsize count = static_cast<jsize>(matches.size());
        out = env->NewObjectArray(count, hitClass, nullptr);
        for (jsize i = 0; i < count; ++i) {
            vector_index_t::match_t m = matches[static_cast<std::size_t>(i)];
            jobject hit = env->NewObject(
                hitClass, ctor,
                static_cast<jlong>(m.member.key), static_cast<jfloat>(m.distance));
            if (hit) {
                env->SetObjectArrayElement(out, i, hit);
                env->DeleteLocalRef(hit);
            }
        }
        env->DeleteLocalRef(hitClass);
        return out;
    } catch (...) {
        if (data) env->ReleaseFloatArrayElements(vector, data, JNI_ABORT);
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeSave(
    JNIEnv* env, jclass, jlong handle, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    vector_index_t* idx = toIndex(handle);
    if (!idx || !path) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return JNI_FALSE;
    bool ok = static_cast<bool>(idx->save(p));
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeLoad(
    JNIEnv* env, jclass, jlong handle, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    vector_index_t* idx = toIndex(handle);
    if (!idx || !path) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return JNI_FALSE;
    bool ok = static_cast<bool>(idx->load(p));
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_USearchVectorIndex_nativeSelfTest(JNIEnv* env, jclass) {
    // In-memory smoke: 3 vectors, cosine search must return the nearest key.
    try {
        constexpr std::size_t dims = 4;
        vector_index_t idx = vector_index_t::make(
            metric_punned_t(dims, metric_kind_t::cos_k, scalar_kind_t::f32_k));
        if (!idx) return JNI_FALSE;
        float a[dims] = {1.f, 0.f, 0.f, 0.f};
        float b[dims] = {0.f, 1.f, 0.f, 0.f};
        float q[dims] = {0.95f, 0.05f, 0.f, 0.f};
        if (!idx.add(7, a)) return JNI_FALSE;
        if (!idx.add(9, b)) return JNI_FALSE;
        auto matches = idx.search(q, 1);
        if (!matches || matches.size() == 0) return JNI_FALSE;
        return matches[0].member.key == 7 ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

} // extern "C"
