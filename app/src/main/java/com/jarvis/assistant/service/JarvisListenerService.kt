package com.jarvis.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.CommandParser
import com.jarvis.assistant.ai.ConversationManager
import com.jarvis.assistant.ai.GeminiClient
import com.jarvis.assistant.commands.CommandExecutor
import com.jarvis.assistant.data.SettingsRepository
import com.jarvis.assistant.voice.SpeakerVerifier
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TextToSpeechManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JARVIS Always-Listening Foreground Service.
 *
 * This service runs continuously in the background with a persistent notification.
 * It manages the complete voice pipeline:
 *
 * 1. Audio Capture → 2. Wake Word Detection → 3. Speaker Verification →
 * 4. Command Recognition → 5. AI Processing → 6. TTS Response → 7. Command Execution
 *
 * Survival Strategy (Background + Screen Off):
 * - Foreground Service with MICROPHONE type → Android won't kill it
 * - PARTIAL_WAKE_LOCK → CPU stays awake even when screen is off
 * - START_STICKY → Service restarts if system kills it
 * - Battery optimization exemption → avoids Doze mode killing
 * - Boot receiver → restarts on device reboot
 */
class JarvisListenerService : Service() {

    companion object {
        private const val TAG = "JarvisService"
        private const val NOTIFICATION_CHANNEL_ID = "jarvis_listener"
        private const val NOTIFICATION_ID = 1001
        private const val WAKELOCK_TAG = "JarvisApp:ListenerWakeLock"

        // Service state shared with UI
        private val _serviceState = MutableStateFlow(JarvisServiceState.IDLE)
        val serviceState: StateFlow<JarvisServiceState> = _serviceState

        private val _lastTranscription = MutableStateFlow("")
        val lastTranscription: StateFlow<String> = _lastTranscription

        private val _lastResponse = MutableStateFlow("")
        val lastResponse: StateFlow<String> = _lastResponse

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        // Callback for UI updates
        var onMessageReceived: ((String, Boolean) -> Unit)? = null // (text, isUser)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Components — initialized lazily to avoid crashes in onCreate
    private var audioCapture: AudioCaptureManager? = null
    private var speechRecognizer: SpeechRecognizerManager? = null
    private var ttsManager: TextToSpeechManager? = null
    private var speakerVerifier: SpeakerVerifier? = null
    private var conversationManager: ConversationManager? = null
    private var commandExecutor: CommandExecutor? = null
    private var geminiClient: GeminiClient? = null

    // WakeLock to keep CPU alive when screen is off
    private var wakeLock: PowerManager.WakeLock? = null

    // State
    private var isListeningForCommand = false
    private var commandAudioBuffer = mutableListOf<ByteArray>()
    private var silenceCounter = 0
    private val SILENCE_THRESHOLD = 15  // ~1.5 seconds of silence at 10ms frames

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JARVIS Service created")

        // Create notification channel FIRST (required before startForeground)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "JARVIS Service onStartCommand")

        // Handle stop action from notification
        if (intent?.action == "STOP_JARVIS") {
            Log.i(TAG, "Stop action received from notification")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start as foreground service IMMEDIATELY (must be within 5 seconds)
        try {
            val notification = createNotification("JARVIS is starting...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Now initialize components safely in background
        serviceScope.launch {
            try {
                initializeComponents()
                startVoicePipeline()
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in voice pipeline", e)
                _serviceState.value = JarvisServiceState.ERROR
                updateNotification("JARVIS encountered an error")
            }
        }

        _isRunning.value = true

        // START_STICKY: If the system kills the service, it will restart it automatically
        return START_STICKY
    }

    /**
     * Initialize all components safely with error handling.
     * Runs on background thread.
     */
    private suspend fun initializeComponents() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Initializing JARVIS components...")

            try {
                audioCapture = AudioCaptureManager(this@JarvisListenerService)
                speechRecognizer = SpeechRecognizerManager(this@JarvisListenerService)
                speakerVerifier = SpeakerVerifier()
                conversationManager = ConversationManager()
                commandExecutor = CommandExecutor(this@JarvisListenerService)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing components", e)
            }

            // Initialize TTS on main thread (TTS requires it)
            withContext(Dispatchers.Main) {
                try {
                    ttsManager = TextToSpeechManager(this@JarvisListenerService)
                    ttsManager?.initialize {
                        Log.i(TAG, "TTS ready")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing TTS", e)
                }
            }

            // Load API key and create Gemini client
            try {
                val settings = SettingsRepository(this@JarvisListenerService)
                val apiKey = settings.apiKey
                if (apiKey.isNotBlank()) {
                    geminiClient = GeminiClient(apiKey)
                    Log.i(TAG, "Gemini client initialized")
                } else {
                    Log.w(TAG, "No Gemini API key — running in offline mode")
                }

                // Restore voice profile
                val voiceProfile = settings.voiceProfile
                voiceProfile?.let { speakerVerifier?.restoreProfile(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
            }

            Log.i(TAG, "All components initialized")
        }
    }

    /**
     * Acquire a partial WakeLock to keep the CPU running when the screen is off.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                acquire()
            }
            Log.i(TAG, "WakeLock acquired — CPU will stay awake")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    /**
     * Release the WakeLock when the service is destroyed.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
        wakeLock = null
    }

    /**
     * Request battery optimization exemption from the user.
     */
    private fun requestBatteryOptimizationExemption() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.i(TAG, "Requested battery optimization exemption")
            } else {
                Log.i(TAG, "Already exempt from battery optimization ✓")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request battery exemption: ${e.message}")
        }
    }

    /**
     * Called when the system is about to destroy the service.
     * Reschedule the service to restart via AlarmManager as a safety net.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed — scheduling service restart")
        scheduleServiceRestart()
    }

    /**
     * Schedule the service to restart after a short delay using AlarmManager.
     */
    private fun scheduleServiceRestart() {
        try {
            val restartIntent = Intent(this, JarvisListenerService::class.java)
            val pendingIntent = PendingIntent.getService(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3000, // Restart after 3 seconds
                pendingIntent
            )
            Log.i(TAG, "Service restart scheduled in 3 seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart", e)
        }
    }

    /**
     * Start the complete voice pipeline.
     */
    private suspend fun startVoicePipeline() {
        Log.i(TAG, "Starting voice pipeline...")

        // Acquire WakeLock for background operation
        withContext(Dispatchers.Main) {
            acquireWakeLock()
        }

        // Check if model needs downloading (first launch)
        val modelDir = java.io.File(filesDir, "vosk-model")
        val needsDownload = !modelDir.exists() || (modelDir.list()?.isEmpty() != false)

        if (needsDownload) {
            _serviceState.value = JarvisServiceState.DOWNLOADING
            updateNotification("JARVIS is downloading voice model (first launch)...")
            // Give TTS a moment to initialize
            delay(2000)
            withContext(Dispatchers.Main) {
                ttsManager?.speak("Welcome, Sir. Downloading my voice recognition model. This will only happen once and takes about a minute.")
            }
        }

        // Initialize Vosk model (downloads if needed)
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Log.e(TAG, "Speech recognizer not initialized")
            _serviceState.value = JarvisServiceState.ERROR
            updateNotification("JARVIS speech error")
            return
        }

        val modelLoaded = recognizer.initialize()
        if (!modelLoaded) {
            Log.e(TAG, "Failed to load Vosk model")
            _serviceState.value = JarvisServiceState.ERROR
            updateNotification("JARVIS: Voice model failed to load")
            withContext(Dispatchers.Main) {
                ttsManager?.speak("I apologize, Sir. The speech model could not be loaded. Please check your internet connection and restart.")
            }
            return
        }

        Log.i(TAG, "Vosk model loaded successfully!")

        // Create wake word recognizer
        val wakeWordRecognizer = recognizer.createWakeWordRecognizer()
        if (wakeWordRecognizer == null) {
            Log.e(TAG, "Failed to create wake word recognizer")
            _serviceState.value = JarvisServiceState.ERROR
            return
        }

        // Create command recognizer
        recognizer.createCommandRecognizer()

        _serviceState.value = JarvisServiceState.IDLE
        updateNotification("JARVIS is standing by...")

        // Greet on first successful start
        if (needsDownload) {
            delay(500)
            withContext(Dispatchers.Main) {
                ttsManager?.speak("Voice model loaded. I'm ready, Sir. Say Jarvis to activate me.")
            }
        }

        // Start audio capture and feed into the pipeline
        val capture = audioCapture
        if (capture == null) {
            Log.e(TAG, "Audio capture not initialized")
            _serviceState.value = JarvisServiceState.ERROR
            return
        }

        val captureStarted = capture.startCapture(serviceScope) { buffer, size ->
            processAudio(buffer, size)
        }

        if (captureStarted) {
            Log.i(TAG, "Voice pipeline started — listening for wake word")
        } else {
            Log.e(TAG, "Failed to start audio capture")
            _serviceState.value = JarvisServiceState.ERROR
            updateNotification("JARVIS: Microphone unavailable")
            withContext(Dispatchers.Main) {
                ttsManager?.speak("I cannot access the microphone, Sir. Please check the app permissions.")
            }
        }
    }

    private var lastConfiguredApiKey: String = ""

    /**
     * Get or dynamically update the GeminiClient from SettingsRepository.
     */
    private fun getOrUpdateGeminiClient(): GeminiClient? {
        try {
            val settings = SettingsRepository(this@JarvisListenerService)
            val currentKey = settings.apiKey.trim()
            if (currentKey.isBlank()) {
                geminiClient = null
                return null
            }
            if (geminiClient == null || lastConfiguredApiKey != currentKey) {
                lastConfiguredApiKey = currentKey
                geminiClient = GeminiClient(currentKey)
                Log.i(TAG, "Gemini client updated with key (length: ${currentKey.length})")
            }
            return geminiClient
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving Gemini client", e)
            return null
        }
    }

    /**
     * Process incoming audio data through the pipeline.
     */
    private fun processAudio(buffer: ByteArray, size: Int) {
        try {
            // Mute / drop audio while JARVIS is speaking or during echo cooldown window
            if (ttsManager?.isSpeakingOrCoolingDown() == true) {
                return
            }

            if (isListeningForCommand) {
                processCommandAudio(buffer, size)
            } else {
                processWakeWordAudio(buffer, size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
        }
    }

    /**
     * Listen for the wake word "Jarvis".
     */
    private fun processWakeWordAudio(buffer: ByteArray, size: Int) {
        val recognizer = speechRecognizer ?: return
        val result = recognizer.processWakeWord(buffer, size)

        if (result.detected) {
            Log.i(TAG, "🎯 Wake word detected! Confidence: ${result.confidence}")

            // Verify speaker identity
            val capture = audioCapture ?: return
            val audioShorts = capture.convertToShortArray(buffer, size)
            val verifier = speakerVerifier
            val (isMatch, similarity) = verifier?.verifyVoice(audioShorts) ?: Pair(true, 1.0f)

            if (isMatch) {
                Log.i(TAG, "✅ Speaker verified (similarity: $similarity)")
                onWakeWordActivated()
            } else {
                Log.w(TAG, "❌ Speaker rejected (similarity: $similarity)")
            }
        }
    }

    /**
     * Handle wake word activation — transition to command listening mode.
     */
    private fun onWakeWordActivated() {
        serviceScope.launch {
            _serviceState.value = JarvisServiceState.SPEAKING
            updateNotification("JARVIS: Yes, Sir?")

            // Speak acknowledgment and wait for speech to finish completely
            ttsManager?.speakAndWait("Yes, Sir?")
            delay(350) // Post-speech pause to guarantee no speaker echo in mic buffer

            // Now reset the recognizer and start listening for the user's command
            speechRecognizer?.resetCommandRecognizer()
            commandAudioBuffer.clear()
            silenceCounter = 0
            _lastTranscription.value = ""
            isListeningForCommand = true
            _serviceState.value = JarvisServiceState.LISTENING
            updateNotification("JARVIS is listening to you...")
        }
    }

    /**
     * Process audio for command recognition (after wake word detected).
     */
    private fun processCommandAudio(buffer: ByteArray, size: Int) {
        val recognizer = speechRecognizer ?: return
        val result = recognizer.processCommand(buffer, size)

        if (result.text.isNotBlank()) {
            _lastTranscription.value = result.text
            silenceCounter = 0
        } else {
            silenceCounter++
        }

        // Check if the user has stopped speaking (silence detected)
        if (silenceCounter >= SILENCE_THRESHOLD && _lastTranscription.value.isNotBlank()) {
            val finalText = recognizer.getFinalCommandResult().ifBlank {
                _lastTranscription.value
            }

            if (finalText.isNotBlank()) {
                Log.i(TAG, "📝 Command received: \"$finalText\"")
                onCommandReceived(finalText)
            }

            // Reset to wake word mode
            isListeningForCommand = false
            silenceCounter = 0
        }

        // Timeout: stop listening after ~10 seconds of no speech
        if (silenceCounter > 100) {
            Log.w(TAG, "Command timeout — no speech detected")
            isListeningForCommand = false
            silenceCounter = 0
            _serviceState.value = JarvisServiceState.IDLE
            updateNotification("JARVIS is standing by...")
        }
    }

    /**
     * Handle a received voice command.
     */
    private fun onCommandReceived(command: String) {
        _serviceState.value = JarvisServiceState.THINKING
        updateNotification("JARVIS is processing...")
        onMessageReceived?.invoke(command, true)

        serviceScope.launch {
            try {
                val client = getOrUpdateGeminiClient()

                if (client != null) {
                    conversationManager?.addUserMessage(command)

                    val result = client.chat(command, conversationManager?.history ?: emptyList())

                    result.fold(
                        onSuccess = { response ->
                            val parsed = CommandParser.parse(response)
                            conversationManager?.addAssistantMessage(parsed.spokenText)
                            respondAndExecute(parsed.spokenText, parsed.action)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Gemini API error: ${error.message}", error)
                            val offlineFallback = CommandParser.parseOfflineCommand(command)
                            if (offlineFallback != null) {
                                val response = "I'm having trouble connecting to AI, but executing offline, Sir."
                                respondAndExecute(response, offlineFallback)
                            } else {
                                val errorMsg = error.message ?: ""
                                val userFeedback = when {
                                    errorMsg.contains("API key", ignoreCase = true) || errorMsg.contains("API_KEY", ignoreCase = true) ->
                                        "Your Gemini API key appears to be invalid, Sir. Please check your key in Settings."
                                    errorMsg.contains("Rate limit", ignoreCase = true) || errorMsg.contains("429") ->
                                        "We have reached the API rate limit, Sir. Please try again shortly."
                                    errorMsg.contains("Unable to resolve host", ignoreCase = true) ||
                                    errorMsg.contains("Failed to connect", ignoreCase = true) ||
                                    errorMsg.contains("timeout", ignoreCase = true) ->
                                        "I cannot connect to the internet right now, Sir. Please check your network."
                                    else ->
                                        "I apologize, Sir. I was unable to process that ($errorMsg)."
                                }
                                respondWithSpeech(userFeedback)
                            }
                        }
                    )
                } else {
                    // No API key configured
                    val offlineAction = CommandParser.parseOfflineCommand(command)
                    if (offlineAction != null) {
                        val response = "Executing, Sir."
                        respondAndExecute(response, offlineAction)
                    } else {
                        respondWithSpeech("Please configure your Gemini API key in Settings, Sir, so I can converse with you.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                respondWithSpeech("I encountered an error, Sir. Please try again.")
            }
        }
    }

    /**
     * Respond with speech and optionally execute a command action.
     */
    private suspend fun respondAndExecute(speechText: String, action: com.jarvis.assistant.ai.JarvisAction?) {
        _serviceState.value = JarvisServiceState.SPEAKING
        updateNotification("JARVIS is speaking...")
        _lastResponse.value = speechText
        onMessageReceived?.invoke(speechText, false)

        ttsManager?.speakAndWait(speechText)
        delay(350) // Post-speech pause to guarantee no speaker echo

        // Execute the action after speaking
        action?.let {
            try {
                commandExecutor?.execute(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
            }
        }

        speechRecognizer?.resetCommandRecognizer()
        _serviceState.value = JarvisServiceState.IDLE
        updateNotification("JARVIS is standing by...")
    }

    /**
     * Respond with speech only.
     */
    private suspend fun respondWithSpeech(text: String) {
        _serviceState.value = JarvisServiceState.SPEAKING
        updateNotification("JARVIS is speaking...")
        _lastResponse.value = text
        onMessageReceived?.invoke(text, false)

        ttsManager?.speakAndWait(text)
        delay(350) // Post-speech pause

        speechRecognizer?.resetCommandRecognizer()
        _serviceState.value = JarvisServiceState.IDLE
        updateNotification("JARVIS is standing by...")
    }

    /**
     * Create the notification channel (required for Android 8+).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "JARVIS Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "JARVIS voice assistant is actively listening"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Create the persistent foreground notification.
     */
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Add Stop action to notification
        val stopIntent = Intent(this, JarvisListenerService::class.java).apply {
            action = "STOP_JARVIS"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop JARVIS", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Update the notification text.
     */
    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "JARVIS Service destroyed")
        _isRunning.value = false
        _serviceState.value = JarvisServiceState.IDLE

        try {
            audioCapture?.stopCapture()
            speechRecognizer?.shutdown()
            ttsManager?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        releaseWakeLock()
        serviceScope.cancel()
    }
}

/**
 * JARVIS service states.
 */
enum class JarvisServiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    DOWNLOADING,
    ERROR
}
