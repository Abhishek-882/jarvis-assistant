package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ui.components.ArcReactorAnimation
import com.jarvis.assistant.ui.components.JarvisState
import com.jarvis.assistant.ui.components.WaveformVisualizer
import com.jarvis.assistant.ui.theme.*

/**
 * Voice Enrollment Screen — one-time setup to record the user's voice profile.
 *
 * Flow:
 * 1. Welcome/explanation
 * 2. Record 5 phrases (each ~3-5 seconds)
 * 3. Processing and profile creation
 * 4. Success/failure feedback
 */
@Composable
fun EnrollmentScreen(
    currentStep: Int,
    totalSteps: Int,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    enrollmentComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val phrases = listOf(
        "Hello Jarvis, I am your creator",
        "Set an alarm for tomorrow morning",
        "What is the weather like today",
        "Open my favorite music app",
        "Tell me something interesting"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JarvisBgPrimary, JarvisBgSecondary, JarvisBgPrimary)
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "VOICE ENROLLMENT",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Thin,
                letterSpacing = 6.sp,
                color = JarvisCyan
            )
        )

        Text(
            text = "Train JARVIS to recognize your voice",
            style = MaterialTheme.typography.bodySmall.copy(
                color = JarvisTextMuted,
                letterSpacing = 1.sp
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!enrollmentComplete) {
            // === Recording Phase ===

            // Progress indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                for (i in 0 until totalSteps) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i < currentStep -> JarvisGreen
                                    i == currentStep -> JarvisCyan
                                    else -> JarvisBorder
                                }
                            )
                    )
                }
            }

            // Step counter
            Text(
                text = "Step ${currentStep + 1} of $totalSteps",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = JarvisTextSecondary,
                    letterSpacing = 2.sp
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Arc reactor visualization
            ArcReactorAnimation(
                state = if (isRecording) JarvisState.LISTENING else JarvisState.IDLE,
                modifier = Modifier.size(160.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Instruction
            Text(
                text = "Please read aloud:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = JarvisTextMuted
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phrase to read
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(JarvisBgCard.copy(alpha = 0.5f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\"${phrases.getOrElse(currentStep) { "Hello Jarvis" }}\"",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform
            WaveformVisualizer(
                isActive = isRecording,
                color = JarvisGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Record button
            FloatingActionButton(
                onClick = {
                    if (isRecording) onStopRecording() else onStartRecording()
                },
                containerColor = if (isRecording) JarvisRed else JarvisGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRecording) "Recording... Tap to stop" else "Tap to start recording",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isRecording) JarvisGreen else JarvisTextMuted
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip Enrollment",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = JarvisTextMuted,
                        letterSpacing = 1.sp
                    )
                )
            }
        } else {
            // === Enrollment Complete ===
            Spacer(modifier = Modifier.weight(0.3f))

            ArcReactorAnimation(
                state = JarvisState.IDLE,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = JarvisGreen,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Voice Enrolled Successfully",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Light,
                    color = JarvisGreen
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "JARVIS will now only respond to your voice",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = JarvisTextSecondary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.5f))

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisBgPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "ACTIVATE JARVIS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
