package com.jarvis.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Boot receiver to start JARVIS service when the device boots up.
 *
 * Handles multiple boot events for maximum compatibility across OEMs:
 * - BOOT_COMPLETED (standard Android)
 * - QUICKBOOT_POWERON (HTC, some others)
 * - LOCKED_BOOT_COMPLETED (direct boot aware)
 *
 * Only starts the service if the user has enabled "Start on boot" in settings.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JarvisBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            Log.i(TAG, "Boot event received: $action")

            val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)
            val hasApiKey = prefs.getString("gemini_api_key", "")?.isNotBlank() == true

            if (startOnBoot) {
                Log.i(TAG, "Starting JARVIS service on boot (API key: ${if (hasApiKey) "configured" else "missing"})")

                val serviceIntent = Intent(context, JarvisListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.i(TAG, "Start on boot disabled — JARVIS will not auto-start")
            }
        }
    }
}
