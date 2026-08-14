package com.engine.nativeai

import android.app.Activity
import android.os.Bundle

// Placeholder launcher. UI arrives in later phases; phase 2 needs this to
// measure cold start + memory on the device.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.app_name)
    }
}
