package com.jarvis.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.*

/**
 * Chat message data.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Chat bubble component — JARVIS messages on the left, user messages on the right.
 * JARVIS bubbles have a cyan glow border, user bubbles are dark.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (!message.isUser) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    JarvisCyan.copy(alpha = 0.5f),
                                    JarvisBlue.copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomEnd = 16.dp,
                                bottomStart = 16.dp
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(
                    RoundedCornerShape(
                        topStart = if (message.isUser) 16.dp else 4.dp,
                        topEnd = if (message.isUser) 4.dp else 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp
                    )
                )
                .background(
                    if (message.isUser) {
                        Brush.linearGradient(
                            colors = listOf(
                                JarvisBgElevated,
                                JarvisBgCard
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                JarvisBgCard.copy(alpha = 0.6f),
                                JarvisBgSecondary.copy(alpha = 0.8f)
                            )
                        )
                    }
                )
                .padding(12.dp)
        ) {
            // Sender label
            Text(
                text = if (message.isUser) "YOU" else "JARVIS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = if (message.isUser) JarvisTextMuted else JarvisCyan
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Message text
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = JarvisTextPrimary,
                    lineHeight = 20.sp
                )
            )
        }
    }
}
