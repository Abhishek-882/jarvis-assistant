package com.jarvis.assistant.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.*

/**
 * JARVIS Settings Screen.
 *
 * Configurable options:
 * - Gemini API key
 * - Always-listening toggle
 * - Start on boot
 * - Wake word sensitivity
 * - Voice verification strictness
 * - TTS voice settings
 * - JARVIS honorific (Sir/Ma'am)
 * - Re-enroll voice
 * - Clear conversation history
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    alwaysListening: Boolean,
    onAlwaysListeningChange: (Boolean) -> Unit,
    startOnBoot: Boolean,
    onStartOnBootChange: (Boolean) -> Unit,
    wakeWordSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    voiceStrictness: Float,
    onStrictnessChange: (Float) -> Unit,
    ttsPitch: Float,
    onPitchChange: (Float) -> Unit,
    ttsSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    honorific: String,
    onHonorificChange: (String) -> Unit,
    isVoiceEnrolled: Boolean,
    onReEnrollVoice: () -> Unit,
    onClearHistory: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JarvisBgPrimary, JarvisBgSecondary)
                )
            )
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Light,
                        color = JarvisCyan
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisTextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = JarvisBgPrimary
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // === API Configuration ===
            SettingsSection(title = "AI CONFIGURATION", icon = Icons.Default.Key) {
                // API Key input
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = {
                        Text("Gemini API Key", color = JarvisTextMuted)
                    },
                    placeholder = {
                        Text("Enter your API key from AI Studio", color = JarvisTextMuted.copy(alpha = 0.5f))
                    },
                    visualTransformation = if (showApiKey)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = JarvisTextMuted
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        cursorColor = JarvisCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Get a free key at aistudio.google.com",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = JarvisCyan.copy(alpha = 0.7f)
                    )
                )
            }

            // === Listening Settings ===
            SettingsSection(title = "LISTENING", icon = Icons.Default.Mic) {
                SettingsSwitch(
                    title = "Always Listening",
                    subtitle = "Keep microphone active for wake word detection",
                    checked = alwaysListening,
                    onCheckedChange = onAlwaysListeningChange
                )

                SettingsSwitch(
                    title = "Start on Boot",
                    subtitle = "Automatically start JARVIS when device boots",
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSlider(
                    title = "Wake Word Sensitivity",
                    value = wakeWordSensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.5f..1.0f,
                    valueLabel = "${(wakeWordSensitivity * 100).toInt()}%"
                )
            }

            // === Voice Security ===
            SettingsSection(title = "VOICE SECURITY", icon = Icons.Default.Security) {
                SettingsSlider(
                    title = "Voice Verification Strictness",
                    value = voiceStrictness,
                    onValueChange = onStrictnessChange,
                    valueRange = 0.5f..0.95f,
                    valueLabel = "${(voiceStrictness * 100).toInt()}%"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Voice Profile",
                            style = MaterialTheme.typography.bodyMedium.copy(color = JarvisTextPrimary)
                        )
                        Text(
                            text = if (isVoiceEnrolled) "Enrolled ✓" else "Not enrolled",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isVoiceEnrolled) JarvisGreen else JarvisGold
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onReEnrollVoice,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisCyan
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(JarvisCyan, JarvisBlue))
                        )
                    ) {
                        Text(if (isVoiceEnrolled) "Re-enroll" else "Enroll Voice")
                    }
                }
            }

            // === Voice Output ===
            SettingsSection(title = "VOICE OUTPUT", icon = Icons.Default.VolumeUp) {
                SettingsSlider(
                    title = "Pitch",
                    value = ttsPitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.5f..1.5f,
                    valueLabel = "%.2f".format(ttsPitch)
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsSlider(
                    title = "Speed",
                    value = ttsSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    valueLabel = "%.2fx".format(ttsSpeed)
                )
            }

            // === Personalization ===
            SettingsSection(title = "PERSONALIZATION", icon = Icons.Default.Person) {
                // Honorific selection
                Text(
                    text = "How should JARVIS address you?",
                    style = MaterialTheme.typography.bodyMedium.copy(color = JarvisTextPrimary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Sir", "Ma'am", "Boss", "Chief").forEach { option ->
                        FilterChip(
                            selected = honorific == option,
                            onClick = { onHonorificChange(option) },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisCyan,
                                containerColor = JarvisBgCard,
                                labelColor = JarvisTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = JarvisBorder,
                                selectedBorderColor = JarvisCyan,
                                enabled = true,
                                selected = honorific == option
                            )
                        )
                    }
                }
            }

            // === Data ===
            SettingsSection(title = "DATA", icon = Icons.Default.Storage) {
                OutlinedButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = JarvisRed
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(JarvisRed, JarvisRed.copy(alpha = 0.5f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Conversation History")
                }
            }

            // Version info
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "JARVIS v1.0.0 • Powered by Gemini",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = JarvisTextMuted,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// === Reusable Settings Components ===

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JarvisCyan.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = JarvisCyan.copy(alpha = 0.7f)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = JarvisBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .background(JarvisBgCard.copy(alpha = 0.5f))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = JarvisTextPrimary)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = JarvisTextMuted)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisCyan,
                checkedTrackColor = JarvisCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = JarvisTextMuted,
                uncheckedTrackColor = JarvisBgSecondary
            )
        )
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = JarvisTextPrimary)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = JarvisCyan,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = JarvisCyan,
                activeTrackColor = JarvisCyan,
                inactiveTrackColor = JarvisBorder
            )
        )
    }
}
