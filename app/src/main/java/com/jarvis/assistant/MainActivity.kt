package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.ai.JarvisPersonality
import com.jarvis.assistant.data.SettingsRepository
import com.jarvis.assistant.service.JarvisListenerService
import com.jarvis.assistant.ui.components.ChatMessage
import com.jarvis.assistant.ui.screens.EnrollmentScreen
import com.jarvis.assistant.ui.screens.HomeScreen
import com.jarvis.assistant.ui.screens.SettingsScreen
import com.jarvis.assistant.ui.theme.JarvisTheme

/**
 * JARVIS Main Activity — the entry point of the application.
 *
 * Handles:
 * - Permission management (microphone, phone, contacts, notifications)
 * - Navigation between Home, Settings, and Enrollment screens
 * - Service lifecycle management (start/stop JARVIS listener)
 * - Settings persistence
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "JarvisMain"
    }

    private lateinit var settings: SettingsRepository

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            Log.i(TAG, "Microphone permission granted — starting service")
            startJarvisService()
        } else {
            Log.w(TAG, "Microphone permission denied")
            Toast.makeText(this, "JARVIS needs microphone permission to function", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = SettingsRepository(this)

        // Apply saved personality settings
        JarvisPersonality.userHonorific = settings.userHonorific

        // Set up the message callback from the service
        val messages = mutableStateListOf<ChatMessage>()
        JarvisListenerService.onMessageReceived = { text, isUser ->
            messages.add(ChatMessage(text = text, isUser = isUser))
        }

        setContent {
            JarvisTheme {
                JarvisApp(
                    settings = settings,
                    messages = messages,
                    onStartService = { requestPermissionsAndStart() },
                    onStopService = { stopJarvisService() }
                )
            }
        }

        // Auto-start service if mic permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Mic permission already granted, auto-starting service")
            startJarvisService()
        }
    }

    /**
     * Request all necessary permissions before starting the service.
     */
    private fun requestPermissionsAndStart() {
        // Check mic permission first — this is the only REQUIRED one
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startJarvisService()
            return
        }

        // Request permissions — only include what's needed
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        // Android 13+ requires POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Start the JARVIS foreground listener service.
     */
    private fun startJarvisService() {
        try {
            val intent = Intent(this, JarvisListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.i(TAG, "JARVIS service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start JARVIS service", e)
            Toast.makeText(this, "Failed to start JARVIS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Stop the JARVIS listener service.
     */
    private fun stopJarvisService() {
        try {
            val intent = Intent(this, JarvisListenerService::class.java)
            stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop JARVIS service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        JarvisListenerService.onMessageReceived = null
    }
}

/**
 * Main Composable — navigation host for JARVIS.
 */
@Composable
fun JarvisApp(
    settings: SettingsRepository,
    messages: List<ChatMessage>,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val navController = rememberNavController()

    // Settings state
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var alwaysListening by remember { mutableStateOf(settings.alwaysListening) }
    var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
    var wakeWordSensitivity by remember { mutableStateOf(settings.wakeWordSensitivity) }
    var voiceStrictness by remember { mutableStateOf(settings.voiceStrictness) }
    var ttsPitch by remember { mutableStateOf(settings.ttsPitch) }
    var ttsSpeed by remember { mutableStateOf(settings.ttsSpeed) }
    var honorific by remember { mutableStateOf(settings.userHonorific) }
    var isVoiceEnrolled by remember { mutableStateOf(settings.isVoiceEnrolled) }

    // Enrollment state
    var enrollmentStep by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var enrollmentComplete by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // === Home Screen ===
        composable("home") {
            HomeScreen(
                onSettingsClick = { navController.navigate("settings") },
                onStartService = onStartService,
                onStopService = onStopService,
                messages = messages
            )
        }

        // === Settings Screen ===
        composable("settings") {
            SettingsScreen(
                apiKey = apiKey,
                onApiKeyChange = { newKey ->
                    apiKey = newKey
                    settings.apiKey = newKey
                },
                alwaysListening = alwaysListening,
                onAlwaysListeningChange = { value ->
                    alwaysListening = value
                    settings.alwaysListening = value
                },
                startOnBoot = startOnBoot,
                onStartOnBootChange = { value ->
                    startOnBoot = value
                    settings.startOnBoot = value
                },
                wakeWordSensitivity = wakeWordSensitivity,
                onSensitivityChange = { value ->
                    wakeWordSensitivity = value
                    settings.wakeWordSensitivity = value
                },
                voiceStrictness = voiceStrictness,
                onStrictnessChange = { value ->
                    voiceStrictness = value
                    settings.voiceStrictness = value
                },
                ttsPitch = ttsPitch,
                onPitchChange = { value ->
                    ttsPitch = value
                    settings.ttsPitch = value
                },
                ttsSpeed = ttsSpeed,
                onSpeedChange = { value ->
                    ttsSpeed = value
                    settings.ttsSpeed = value
                },
                honorific = honorific,
                onHonorificChange = { value ->
                    honorific = value
                    settings.userHonorific = value
                    JarvisPersonality.userHonorific = value
                },
                isVoiceEnrolled = isVoiceEnrolled,
                onReEnrollVoice = {
                    enrollmentStep = 0
                    enrollmentComplete = false
                    navController.navigate("enrollment")
                },
                onClearHistory = {
                    // Messages are in-memory for now
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // === Voice Enrollment Screen ===
        composable("enrollment") {
            EnrollmentScreen(
                currentStep = enrollmentStep,
                totalSteps = 5,
                isRecording = isRecording,
                onStartRecording = {
                    isRecording = true
                    // Audio recording would be handled by the ViewModel in production
                },
                onStopRecording = {
                    isRecording = false
                    enrollmentStep++
                    if (enrollmentStep >= 5) {
                        enrollmentComplete = true
                        isVoiceEnrolled = true
                        settings.isVoiceEnrolled = true
                    }
                },
                onSkip = {
                    navController.popBackStack()
                },
                onComplete = {
                    navController.popBackStack()
                    navController.popBackStack()
                },
                enrollmentComplete = enrollmentComplete
            )
        }
    }
}
