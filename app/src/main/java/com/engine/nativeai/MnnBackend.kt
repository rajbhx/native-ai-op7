package com.engine.nativeai

/**
 * MNN runtime probe (S6). libMNN.so is bundled for arm64-v8a and probed via
 * a dlopen JNI shim — no model inference is claimed until a benchmark gate
 * validates a real MNN model on-device (no fake capabilities). When the
 * library is missing or unloadable the backend honestly reports unavailable.
 */
class MnnBackend : InferenceBackend {

    override val available: Boolean get() = LIB_LOADED && nativeAvailable()

    override val runtime: RuntimeKind get() = RuntimeKind.MNN

    override fun status(): String =
        if (available) "MNN 3.6.1 (arm64-v8a) ready" else "MNN unavailable (libMNN.so not loadable)"

    private external fun nativeAvailable(): Boolean

    companion object {
        /** Version metadata is pinned to the bundled release, verified in CI. */
        const val BUNDLED_VERSION = "3.6.1"

        /** The probe lives in native-lib; never call it before the lib is loaded. */
        private val LIB_LOADED = try {
            System.loadLibrary("native-lib")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
