package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    private lateinit var settings: SettingsRepository

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startJarvisService()
        } else {
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
    }

    /**
     * Request all necessary permissions before starting the service.
     */
    private fun requestPermissionsAndStart() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        // Android 13+ requires POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Optional permissions
        val optionalPermissions = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        val allPermissions = requiredPermissions + optionalPermissions
        val notGranted = allPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startJarvisService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    /**
     * Start the JARVIS foreground listener service.
     */
    private fun startJarvisService() {
        val intent = Intent(this, JarvisListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Stop the JARVIS listener service.
     */
    private fun stopJarvisService() {
        val intent = Intent(this, JarvisListenerService::class.java)
        stopService(intent)
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
