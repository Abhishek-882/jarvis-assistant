package com.jarvis.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Settings repository for JARVIS preferences.
 *
 * Stores:
 * - Gemini API key (encrypted, with fallback to standard prefs)
 * - Voice profile data (encrypted, with fallback to standard prefs)
 * - User preferences (standard SharedPreferences)
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private const val TAG = "JarvisSettings"
        private const val PREFS_NAME = "jarvis_settings"
        private const val ENCRYPTED_PREFS_NAME = "jarvis_secure"

        // Default API key placeholder (configured in Settings screen)
        private const val DEFAULT_API_KEY = ""

        // Keys
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_VOICE_PROFILE = "voice_profile"
        const val KEY_ALWAYS_LISTENING = "always_listening"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        const val KEY_VOICE_STRICTNESS = "voice_strictness"
        const val KEY_TTS_PITCH = "tts_pitch"
        const val KEY_TTS_SPEED = "tts_speed"
        const val KEY_USER_HONORIFIC = "user_honorific"
        const val KEY_VOICE_ENROLLED = "voice_enrolled"
        const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, falling back to standard prefs", e)
            // Fallback to regular prefs if encryption fails (some OEM devices have KeyStore bugs)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // === API Key ===
    var apiKey: String
        get() {
            return try {
                val secureVal = securePrefs.getString(KEY_API_KEY, null)
                val standardVal = prefs.getString(KEY_API_KEY, null)
                val stored = secureVal ?: standardVal
                if (stored.isNullOrBlank()) {
                    DEFAULT_API_KEY
                } else {
                    stored.trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading API key", e)
                try {
                    prefs.getString(KEY_API_KEY, DEFAULT_API_KEY)?.trim() ?: DEFAULT_API_KEY
                } catch (e2: Exception) {
                    DEFAULT_API_KEY
                }
            }
        }
        set(value) {
            val trimmed = value.trim()
            try {
                securePrefs.edit().putString(KEY_API_KEY, trimmed).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving API key to securePrefs", e)
            }
            try {
                prefs.edit().putString(KEY_API_KEY, trimmed).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving API key to standard prefs", e)
            }
        }

    // === Voice Profile ===
    var voiceProfile: String?
        get() {
            return try {
                securePrefs.getString(KEY_VOICE_PROFILE, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading voice profile", e)
                null
            }
        }
        set(value) {
            try {
                securePrefs.edit().putString(KEY_VOICE_PROFILE, value).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving voice profile", e)
            }
        }

    var isVoiceEnrolled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENROLLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENROLLED, value).apply()

    // === Listening Settings ===
    var alwaysListening: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_LISTENING, true)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    var wakeWordSensitivity: Float
        get() = prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.80f)
        set(value) = prefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, value).apply()

    var voiceStrictness: Float
        get() = prefs.getFloat(KEY_VOICE_STRICTNESS, 0.70f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_STRICTNESS, value).apply()

    // === TTS Settings ===
    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.05f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    // === Personalization ===
    var userHonorific: String
        get() = prefs.getString(KEY_USER_HONORIFIC, "Sir") ?: "Sir"
        set(value) = prefs.edit().putString(KEY_USER_HONORIFIC, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    /**
     * Clear all settings and stored data.
     */
    fun clearAll() {
        try {
            prefs.edit().clear().apply()
            securePrefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing settings", e)
        }
    }
}
