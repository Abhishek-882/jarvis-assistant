package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.*
import kotlin.math.sin

/**
 * Audio waveform visualizer — shows a live-looking waveform when JARVIS speaks or listens.
 */
@Composable
fun WaveformVisualizer(
    isActive: Boolean,
    color: Color = JarvisCyan,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitude by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Draw multiple overlapping sine waves for a rich waveform
        val waves = listOf(
            WaveConfig(frequency = 3f, amplitudeScale = 0.4f, phaseOffset = 0f, alpha = 0.3f),
            WaveConfig(frequency = 5f, amplitudeScale = 0.25f, phaseOffset = 1.2f, alpha = 0.5f),
            WaveConfig(frequency = 7f, amplitudeScale = 0.15f, phaseOffset = 2.5f, alpha = 0.2f),
            WaveConfig(frequency = 2f, amplitudeScale = 0.6f, phaseOffset = 0.5f, alpha = 0.7f),
        )

        // Center baseline
        drawLine(
            color = color.copy(alpha = 0.1f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        waves.forEach { wave ->
            val path = Path()
            val maxAmplitude = (height / 2) * wave.amplitudeScale * amplitude

            for (x in 0..width.toInt() step 2) {
                val xFloat = x.toFloat()
                val normalizedX = xFloat / width
                val y = centerY + maxAmplitude * sin(
                    (normalizedX * wave.frequency * 2 * Math.PI + phase + wave.phaseOffset).toDouble()
                ).toFloat()

                if (x == 0) {
                    path.moveTo(xFloat, y)
                } else {
                    path.lineTo(xFloat, y)
                }
            }

            drawPath(
                path = path,
                color = color.copy(alpha = wave.alpha * amplitude),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Glow effect at the edges
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF0A0E17),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF0A0E17)
                ),
                startX = 0f,
                endX = width
            ),
            size = size
        )
    }
}

private data class WaveConfig(
    val frequency: Float,
    val amplitudeScale: Float,
    val phaseOffset: Float,
    val alpha: Float
)
