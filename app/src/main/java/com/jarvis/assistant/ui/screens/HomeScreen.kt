package com.jarvis.assistant.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.service.JarvisListenerService
import com.jarvis.assistant.service.JarvisServiceState
import com.jarvis.assistant.ui.components.*
import com.jarvis.assistant.ui.theme.*
import kotlinx.coroutines.launch

/**
 * JARVIS Home Screen — the main interface.
 *
 * Features:
 * - Central Arc Reactor animation showing JARVIS state
 * - Status indicator (STANDBY / LISTENING / PROCESSING / RESPONDING)
 * - Live waveform visualizer
 * - Conversation transcript
 * - Manual mic trigger button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val serviceState by JarvisListenerService.serviceState.collectAsState()
    val isRunning by JarvisListenerService.isRunning.collectAsState()
    val lastTranscription by JarvisListenerService.lastTranscription.collectAsState()

    val jarvisState = when (serviceState) {
        JarvisServiceState.IDLE -> JarvisState.IDLE
        JarvisServiceState.LISTENING -> JarvisState.LISTENING
        JarvisServiceState.THINKING -> JarvisState.THINKING
        JarvisServiceState.SPEAKING -> JarvisState.SPEAKING
        JarvisServiceState.DOWNLOADING -> JarvisState.THINKING  // reuse pulsing animation
        JarvisServiceState.ERROR -> JarvisState.ERROR
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        JarvisBgPrimary,
                        JarvisBgSecondary,
                        JarvisBgPrimary
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // === Top Bar ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // JARVIS title
                Column {
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Thin,
                            letterSpacing = 6.sp,
                            color = JarvisCyan
                        )
                    )
                    Text(
                        text = "Just A Rather Very Intelligent System",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = JarvisTextMuted
                        )
                    )
                }

                // Settings button
                IconButton(
                    onClick = onSettingsClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = JarvisTextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }

            // === Arc Reactor Section ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Arc Reactor
                    ArcReactorAnimation(
                        state = jarvisState,
                        modifier = Modifier.size(220.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status
                    StatusIndicator(state = jarvisState)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live transcription display
                    AnimatedVisibility(
                        visible = serviceState == JarvisServiceState.LISTENING && lastTranscription.isNotBlank()
                    ) {
                        Text(
                            text = "\"$lastTranscription\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = JarvisGreen.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Light
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }

            // === Waveform ===
            WaveformVisualizer(
                isActive = serviceState == JarvisServiceState.LISTENING ||
                        serviceState == JarvisServiceState.SPEAKING,
                color = when (serviceState) {
                    JarvisServiceState.LISTENING -> JarvisGreen
                    JarvisServiceState.SPEAKING -> JarvisBlue
                    else -> JarvisCyan
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // === Divider ===
            HorizontalDivider(
                color = JarvisBorder,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // === Conversation Area ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            ) {
                if (messages.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isRunning) "Say \"Jarvis\" to start..." else "Tap the microphone to activate",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = JarvisTextMuted,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 0.5.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }
            }

            // === Bottom Action Bar ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main mic button
                FloatingActionButton(
                    onClick = {
                        if (isRunning) onStopService() else onStartService()
                    },
                    containerColor = if (isRunning) JarvisRed.copy(alpha = 0.8f) else JarvisCyan,
                    contentColor = if (isRunning) Color.White else JarvisBgPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isRunning) "Stop JARVIS" else "Start JARVIS",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // === HUD Corners (Decorative) ===
        HudCornerDecoration(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        )
        HudCornerDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            flipHorizontal = true
        )
    }
}

/**
 * Decorative HUD corner brackets for the Iron Man feel.
 */
@Composable
private fun HudCornerDecoration(
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(30.dp)
    ) {
        val color = JarvisCyan.copy(alpha = 0.3f)
        val strokeWidth = 1.5.dp.toPx()
        val length = size.minDimension * 0.6f

        val scaleX = if (flipHorizontal) -1f else 1f
        val offsetX = if (flipHorizontal) size.width else 0f

        // Vertical line
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(offsetX + 0f * scaleX, 0f),
            end = androidx.compose.ui.geometry.Offset(offsetX + 0f * scaleX, length),
            strokeWidth = strokeWidth
        )
        // Horizontal line
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(offsetX + 0f * scaleX, 0f),
            end = androidx.compose.ui.geometry.Offset(offsetX + length * scaleX, 0f),
            strokeWidth = strokeWidth
        )
    }
}
