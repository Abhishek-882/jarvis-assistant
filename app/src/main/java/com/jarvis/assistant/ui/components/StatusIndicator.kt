package com.jarvis.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.*

/**
 * Status indicator showing JARVIS's current state with label and color.
 */
@Composable
fun StatusIndicator(
    state: JarvisState,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        JarvisState.IDLE -> "STANDBY"
        JarvisState.LISTENING -> "LISTENING"
        JarvisState.THINKING -> "PROCESSING"
        JarvisState.SPEAKING -> "RESPONDING"
        JarvisState.ERROR -> "ERROR"
    }

    val statusColor by animateColorAsState(
        targetValue = when (state) {
            JarvisState.IDLE -> StatusIdle
            JarvisState.LISTENING -> StatusListening
            JarvisState.THINKING -> StatusThinking
            JarvisState.SPEAKING -> StatusSpeaking
            JarvisState.ERROR -> StatusError
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.IDLE) 2000 else 800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Pulsing status dot
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = statusColor.copy(alpha = dotAlpha * 0.3f),
                radius = size.minDimension / 2 * 1.5f
            )
            drawCircle(
                color = statusColor.copy(alpha = dotAlpha),
                radius = size.minDimension / 2
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = statusColor
            ),
            textAlign = TextAlign.Center
        )
    }
}
