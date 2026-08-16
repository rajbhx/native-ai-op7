package com.engine.nativeai

import android.app.Application

/** Application entry: installs the core error hook once per process. */
class NativeAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoreErrors.installCrashHook()
    }
}
