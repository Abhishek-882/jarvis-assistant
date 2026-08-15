package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS state for the Arc Reactor animation.
 */
enum class JarvisState {
    IDLE,       // Dim, slow pulse
    LISTENING,  // Bright green pulse
    THINKING,   // Gold spinning
    SPEAKING,   // Blue waveform
    ERROR       // Red pulse
}

/**
 * Arc Reactor animation — the central visual element of JARVIS UI.
 * Inspired by Tony Stark's Iron Man arc reactor.
 *
 * Features:
 * - Concentric glowing rings that rotate
 * - Core glow that pulses based on state
 * - Energy arc particles
 * - State-driven color and animation changes
 */
@Composable
fun ArcReactorAnimation(
    state: JarvisState = JarvisState.IDLE,
    modifier: Modifier = Modifier.size(280.dp)
) {
    // --- Animation values ---

    // Continuous rotation for outer rings
    val infiniteTransition = rememberInfiniteTransition(label = "reactor")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisState.THINKING -> 2000
                    JarvisState.SPEAKING -> 4000
                    else -> 8000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val middleRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisState.THINKING -> 3000
                    JarvisState.SPEAKING -> 5000
                    else -> 12000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "middleRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisState.THINKING -> 1500
                    JarvisState.SPEAKING -> 3000
                    else -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Core pulse (breathing effect)
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisState.LISTENING -> 800
                    JarvisState.THINKING -> 500
                    JarvisState.SPEAKING -> 600
                    JarvisState.ERROR -> 400
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // Energy particles
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    // State-driven colors
    val primaryColor = when (state) {
        JarvisState.IDLE -> JarvisCyan
        JarvisState.LISTENING -> JarvisGreen
        JarvisState.THINKING -> JarvisGold
        JarvisState.SPEAKING -> JarvisBlue
        JarvisState.ERROR -> JarvisRed
    }

    val glowColor = when (state) {
        JarvisState.IDLE -> JarvisCyanGlow
        JarvisState.LISTENING -> JarvisGreenGlow
        JarvisState.THINKING -> JarvisGoldGlow
        JarvisState.SPEAKING -> JarvisBlueGlow
        JarvisState.ERROR -> JarvisRedGlow
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.minDimension / 2

        // === Layer 1: Outer glow halo ===
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.3f * corePulse),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius * 1.1f
            ),
            radius = maxRadius * 1.1f,
            center = Offset(centerX, centerY)
        )

        // === Layer 2: Outer ring (segmented, rotating) ===
        rotate(outerRotation, pivot = Offset(centerX, centerY)) {
            drawOuterRing(centerX, centerY, maxRadius * 0.92f, primaryColor, corePulse)
        }

        // === Layer 3: Middle ring (counter-rotating) ===
        rotate(middleRotation, pivot = Offset(centerX, centerY)) {
            drawMiddleRing(centerX, centerY, maxRadius * 0.72f, primaryColor, corePulse)
        }

        // === Layer 4: Inner ring (fast rotation) ===
        rotate(innerRotation, pivot = Offset(centerX, centerY)) {
            drawInnerRing(centerX, centerY, maxRadius * 0.50f, primaryColor, corePulse)
        }

        // === Layer 5: Core glow ===
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f * corePulse),
                    primaryColor.copy(alpha = 0.7f * corePulse),
                    primaryColor.copy(alpha = 0.3f * corePulse),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius * 0.28f
            ),
            radius = maxRadius * 0.28f,
            center = Offset(centerX, centerY)
        )

        // === Layer 6: Core center dot ===
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = maxRadius * 0.06f,
            center = Offset(centerX, centerY)
        )

        // === Layer 7: Energy particles orbiting ===
        drawEnergyParticles(centerX, centerY, maxRadius * 0.82f, particlePhase, primaryColor, corePulse)
    }
}

/**
 * Draw the outer segmented ring with gaps.
 */
private fun DrawScope.drawOuterRing(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    val segmentCount = 12
    val gapDegrees = 6f
    val segmentDegrees = (360f / segmentCount) - gapDegrees

    for (i in 0 until segmentCount) {
        val startAngle = i * (segmentDegrees + gapDegrees)
        drawArc(
            color = color.copy(alpha = 0.6f * alpha),
            startAngle = startAngle,
            sweepAngle = segmentDegrees,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Draw the middle ring with thicker segments and different spacing.
 */
private fun DrawScope.drawMiddleRing(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    val segmentCount = 8
    val gapDegrees = 10f
    val segmentDegrees = (360f / segmentCount) - gapDegrees

    for (i in 0 until segmentCount) {
        val startAngle = i * (segmentDegrees + gapDegrees)
        drawArc(
            color = color.copy(alpha = 0.75f * alpha),
            startAngle = startAngle,
            sweepAngle = segmentDegrees,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Draw the inner ring — a continuous circle with glow.
 */
private fun DrawScope.drawInnerRing(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    // Glow ring
    drawCircle(
        color = color.copy(alpha = 0.2f * alpha),
        radius = radius + 4.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 8.dp.toPx())
    )
    // Sharp ring
    drawCircle(
        color = color.copy(alpha = 0.8f * alpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )

    // Tick marks
    val tickCount = 24
    for (i in 0 until tickCount) {
        val angle = (i * 360f / tickCount) * (PI / 180f)
        val innerR = radius - 6.dp.toPx()
        val outerR = radius - 2.dp.toPx()
        drawLine(
            color = color.copy(alpha = 0.5f * alpha),
            start = Offset(
                cx + innerR * cos(angle).toFloat(),
                cy + innerR * sin(angle).toFloat()
            ),
            end = Offset(
                cx + outerR * cos(angle).toFloat(),
                cy + outerR * sin(angle).toFloat()
            ),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

/**
 * Draw orbiting energy particles.
 */
private fun DrawScope.drawEnergyParticles(
    cx: Float, cy: Float, radius: Float,
    phase: Float, color: Color, alpha: Float
) {
    val particleCount = 6
    for (i in 0 until particleCount) {
        val angle = phase + (i * 2 * PI / particleCount)
        val particleRadius = 3.dp.toPx() * (0.5f + 0.5f * sin(phase * 2 + i).toFloat())
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()

        // Particle glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.8f * alpha),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = particleRadius * 3
            ),
            radius = particleRadius * 3,
            center = Offset(x, y)
        )

        // Particle core
        drawCircle(
            color = Color.White.copy(alpha = 0.9f * alpha),
            radius = particleRadius,
            center = Offset(x, y)
        )
    }
}
