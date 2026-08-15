// MNN presence probe (S6): dlopen the bundled libMNN.so without link-time
// coupling. Absence or load failure reports unavailable — the app never
// claims an MNN runtime it cannot use. No MNN headers required here.
#include <dlfcn.h>
#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_engine_nativeai_MnnBackend_nativeAvailable(JNIEnv*, jobject) {
    void* handle = dlopen("libMNN.so", RTLD_NOW | RTLD_LOCAL);
    if (handle != nullptr) {
        dlclose(handle);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
