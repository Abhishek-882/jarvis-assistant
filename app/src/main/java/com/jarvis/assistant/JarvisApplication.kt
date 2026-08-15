package com.jarvis.assistant

import android.app.Application
import android.util.Log

/**
 * JARVIS Application class — initialization entry point.
 */
class JarvisApplication : Application() {

    companion object {
        private const val TAG = "JarvisApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JARVIS Application initialized")
    }
}
