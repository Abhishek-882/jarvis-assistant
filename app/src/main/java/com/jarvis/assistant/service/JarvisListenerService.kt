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

    // Components
    private lateinit var audioCapture: AudioCaptureManager
    private lateinit var speechRecognizer: SpeechRecognizerManager
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var speakerVerifier: SpeakerVerifier
    private lateinit var conversationManager: ConversationManager
    private lateinit var commandExecutor: CommandExecutor
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

        // Acquire WakeLock — keeps CPU running even when screen is off
        acquireWakeLock()

        // Initialize components
        audioCapture = AudioCaptureManager(this)
        speechRecognizer = SpeechRecognizerManager(this)
        ttsManager = TextToSpeechManager(this)
        speakerVerifier = SpeakerVerifier()
        conversationManager = ConversationManager()
        commandExecutor = CommandExecutor(this)

        // Initialize TTS
        ttsManager.initialize {
            Log.i(TAG, "TTS ready")
        }

        // Load API key and create Gemini client using SettingsRepository
        val settings = SettingsRepository(this)
        val apiKey = settings.apiKey
        if (apiKey.isNotBlank()) {
            geminiClient = GeminiClient(apiKey)
            Log.i(TAG, "Gemini client initialized with API key")
        } else {
            Log.w(TAG, "No Gemini API key — running in offline mode")
        }

        // Restore voice profile
        val voiceProfile = settings.voiceProfile
        voiceProfile?.let { speakerVerifier.restoreProfile(it) }

        createNotificationChannel()

        // Request battery optimization exemption (keeps JARVIS alive in Doze)
        requestBatteryOptimizationExemption()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "JARVIS Service starting")

        // Start as foreground service
        val notification = createNotification("JARVIS is listening...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Initialize and start the voice pipeline
        serviceScope.launch {
            startVoicePipeline()
        }

        _isRunning.value = true

        // START_STICKY: If the system kills the service, it will restart it automatically
        return START_STICKY
    }

    /**
     * Acquire a partial WakeLock to keep the CPU running when the screen is off.
     *
     * This is CRITICAL for always-listening functionality:
     * - Without it, Android will put the CPU to sleep after screen off
     * - AudioRecord stops receiving data when CPU sleeps
     * - PARTIAL_WAKE_LOCK keeps only the CPU awake (not the screen)
     * - Battery impact: ~3-5% per hour (acceptable for an always-on assistant)
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            // Acquire indefinitely (released in onDestroy)
            acquire()
        }
        Log.i(TAG, "WakeLock acquired — CPU will stay awake")
    }

    /**
     * Release the WakeLock when the service is destroyed.
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    /**
     * Request battery optimization exemption from the user.
     *
     * This prevents Android's Doze mode from:
     * - Deferring the service's alarms and network access
     * - Suspending the service during inactivity
     *
     * The user sees a system dialog asking to exempt JARVIS from battery optimization.
     */
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.i(TAG, "Requested battery optimization exemption")
            } catch (e: Exception) {
                Log.w(TAG, "Could not request battery exemption: ${e.message}")
            }
        } else {
            Log.i(TAG, "Already exempt from battery optimization ✓")
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
     * This is a safety net in case the system kills the service.
     */
    private fun scheduleServiceRestart() {
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
    }

    /**
     * Start the complete voice pipeline.
     */
    private suspend fun startVoicePipeline() {
        // Initialize Vosk model
        val modelLoaded = speechRecognizer.initialize()
        if (!modelLoaded) {
            Log.e(TAG, "Failed to load Vosk model — running without speech recognition")
            _serviceState.value = JarvisServiceState.ERROR
            ttsManager.speak("I apologize, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}. The speech model could not be loaded. Please check the app configuration.")
            return
        }

        // Create wake word recognizer
        val wakeWordRecognizer = speechRecognizer.createWakeWordRecognizer()
        if (wakeWordRecognizer == null) {
            Log.e(TAG, "Failed to create wake word recognizer")
            _serviceState.value = JarvisServiceState.ERROR
            return
        }

        // Create command recognizer
        speechRecognizer.createCommandRecognizer()

        _serviceState.value = JarvisServiceState.IDLE
        updateNotification("JARVIS is standing by...")

        // Start audio capture and feed into the pipeline
        audioCapture.startCapture(serviceScope) { buffer, size ->
            processAudio(buffer, size)
        }

        Log.i(TAG, "Voice pipeline started — listening for wake word (screen on/off)")
    }

    /**
     * Process incoming audio data through the pipeline.
     */
    private fun processAudio(buffer: ByteArray, size: Int) {
        if (isListeningForCommand) {
            // We're in command mode — process full speech
            processCommandAudio(buffer, size)
        } else {
            // We're in wake word mode — listen for "Jarvis"
            processWakeWordAudio(buffer, size)
        }
    }

    /**
     * Listen for the wake word "Jarvis".
     */
    private fun processWakeWordAudio(buffer: ByteArray, size: Int) {
        val result = speechRecognizer.processWakeWord(buffer, size)

        if (result.detected) {
            Log.i(TAG, "🎯 Wake word detected! Confidence: ${result.confidence}")

            // Verify speaker identity
            val audioShorts = audioCapture.convertToShortArray(buffer, size)
            val (isMatch, similarity) = speakerVerifier.verifyVoice(audioShorts)

            if (isMatch) {
                Log.i(TAG, "✅ Speaker verified (similarity: $similarity)")
                onWakeWordActivated()
            } else {
                Log.w(TAG, "❌ Speaker rejected (similarity: $similarity)")
                // Don't respond — just keep listening
            }
        }
    }

    /**
     * Handle wake word activation — transition to command listening mode.
     */
    private fun onWakeWordActivated() {
        isListeningForCommand = true
        commandAudioBuffer.clear()
        silenceCounter = 0

        _serviceState.value = JarvisServiceState.LISTENING
        updateNotification("JARVIS is listening to you...")

        // Reset the command recognizer for a fresh utterance
        speechRecognizer.resetCommandRecognizer()

        // Play a subtle acknowledgment
        ttsManager.speak("Yes, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}?")
    }

    /**
     * Process audio for command recognition (after wake word detected).
     */
    private fun processCommandAudio(buffer: ByteArray, size: Int) {
        val result = speechRecognizer.processCommand(buffer, size)

        if (result.text.isNotBlank()) {
            _lastTranscription.value = result.text
            silenceCounter = 0
        } else {
            silenceCounter++
        }

        // Check if the user has stopped speaking (silence detected)
        if (silenceCounter >= SILENCE_THRESHOLD && _lastTranscription.value.isNotBlank()) {
            // Get the final transcription
            val finalText = speechRecognizer.getFinalCommandResult().ifBlank {
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
                // First, try offline command parsing
                val offlineAction = CommandParser.parseOfflineCommand(command)

                if (offlineAction != null && geminiClient == null) {
                    // Execute offline action directly
                    val response = "Right away, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}."
                    respondAndExecute(response, offlineAction)
                    return@launch
                }

                // Use Gemini AI for intelligent response
                val client = geminiClient
                if (client != null) {
                    conversationManager.addUserMessage(command)

                    val result = client.chat(command, conversationManager.history)

                    result.fold(
                        onSuccess = { response ->
                            val parsed = CommandParser.parse(response)
                            conversationManager.addAssistantMessage(parsed.spokenText)
                            respondAndExecute(parsed.spokenText, parsed.action)
                        },
                        onFailure = { error ->
                            // Fallback to offline
                            val offlineFallback = CommandParser.parseOfflineCommand(command)
                            if (offlineFallback != null) {
                                val response = "I'm having trouble connecting, but I can handle that locally, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}."
                                respondAndExecute(response, offlineFallback)
                            } else {
                                val errorResponse = "I apologize, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}. I'm unable to process that request at the moment. ${error.message ?: ""}"
                                respondWithSpeech(errorResponse)
                            }
                        }
                    )
                } else {
                    // No API key configured — offline only
                    if (offlineAction != null) {
                        val response = "Executing, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}."
                        respondAndExecute(response, offlineAction)
                    } else {
                        respondWithSpeech("I need an API key to answer that, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}. Please configure one in the settings.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                respondWithSpeech("I encountered an error, ${com.jarvis.assistant.ai.JarvisPersonality.userHonorific}. Please try again.")
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

        ttsManager.speakAndWait(speechText)

        // Execute the action after speaking
        action?.let {
            commandExecutor.execute(it)
        }

        // Return to idle
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

        ttsManager.speakAndWait(text)

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
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "JARVIS Service destroyed")
        _isRunning.value = false
        _serviceState.value = JarvisServiceState.IDLE

        audioCapture.stopCapture()
        speechRecognizer.shutdown()
        ttsManager.shutdown()
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
    ERROR
}
