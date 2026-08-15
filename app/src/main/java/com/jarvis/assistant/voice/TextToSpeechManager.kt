package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Text-to-Speech manager for JARVIS voice output.
 *
 * Uses Android's built-in TTS engine (free, works offline).
 * Configured with JARVIS-style voice parameters:
 * - Slightly lower pitch (authoritative)
 * - Slightly faster speed (efficient)
 * - British English accent (when available)
 */
class TextToSpeechManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "JarvisTTS"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitCallback: (() -> Unit)? = null

    // JARVIS voice settings
    var pitch: Float = 0.9f        // Slightly lower pitch
    var speechRate: Float = 1.05f  // Slightly faster
    var volume: Float = 1.0f

    /**
     * Initialize the TTS engine.
     */
    fun initialize(onReady: () -> Unit = {}) {
        onInitCallback = onReady
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTts()
                isInitialized = true
                Log.i(TAG, "JARVIS TTS initialized successfully")
                onInitCallback?.invoke()
            } else {
                Log.e(TAG, "JARVIS TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Configure TTS with JARVIS voice parameters.
     */
    private fun configureTts() {
        tts?.let { engine ->
            // Try British English first, fallback to US English
            val britishLocale = Locale("en", "GB")
            val result = engine.setLanguage(britishLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to US English
                engine.setLanguage(Locale.US)
                Log.w(TAG, "British English not available, using US English")
            }

            engine.setPitch(pitch)
            engine.setSpeechRate(speechRate)
        }
    }

    /**
     * Speak the given text as JARVIS.
     * Returns immediately — speech happens asynchronously.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, queuing: $text")
            return
        }

        val utteranceId = "jarvis_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "JARVIS speaking: $text")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "JARVIS finished speaking")
                onComplete?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "JARVIS TTS error for: $text")
                onComplete?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "JARVIS TTS error code $errorCode for: $text")
                onComplete?.invoke()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speak the given text and suspend until speech completes.
     */
    suspend fun speakAndWait(text: String) = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        speak(text) {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        continuation.invokeOnCancellation {
            stop()
        }
    }

    /**
     * Stop any ongoing speech (for barge-in support).
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if JARVIS is currently speaking.
     */
    val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

    /**
     * Update voice parameters.
     */
    fun updateVoiceSettings(newPitch: Float? = null, newRate: Float? = null) {
        newPitch?.let {
            pitch = it
            tts?.setPitch(it)
        }
        newRate?.let {
            speechRate = it
            tts?.setSpeechRate(it)
        }
    }

    /**
     * Release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.i(TAG, "JARVIS TTS shutdown")
    }
}
