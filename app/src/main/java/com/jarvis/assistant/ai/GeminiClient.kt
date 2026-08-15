package com.jarvis.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gemini API client for JARVIS AI conversations.
 *
 * Uses Google Gemini Flash model (free tier) for:
 * - Natural conversation with JARVIS personality
 * - Command extraction for phone actions
 * - Context-aware multi-turn dialogue
 */
class GeminiClient(
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-2.0-flash"
        private const val MAX_RETRIES = 2
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Send a message to Gemini and get a response.
     *
     * @param userMessage The user's transcribed voice command
     * @param conversationHistory Previous messages for context
     * @return The AI response text, or an error message
     */
    suspend fun chat(
        userMessage: String,
        conversationHistory: List<ConversationMessage> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(userMessage, conversationHistory)
            val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .build()

            var lastError: Exception? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    val response = client.newCall(request).execute()

                    when (response.code) {
                        200 -> {
                            val responseBody = response.body?.string()
                                ?: return@withContext Result.failure(IOException("Empty response body"))
                            val text = extractTextFromResponse(responseBody)
                            return@withContext Result.success(text)
                        }
                        429 -> {
                            // Rate limited — wait and retry
                            if (attempt < MAX_RETRIES) {
                                kotlinx.coroutines.delay(2000L * (attempt + 1))
                                continue
                            }
                            return@withContext Result.failure(
                                IOException("Rate limited. Please try again in a moment, ${JarvisPersonality.userHonorific}.")
                            )
                        }
                        else -> {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            return@withContext Result.failure(
                                IOException("API error ${response.code}: $errorBody")
                            )
                        }
                    }
                } catch (e: IOException) {
                    lastError = e
                    if (attempt < MAX_RETRIES) {
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                    }
                }
            }

            Result.failure(lastError ?: IOException("Failed after $MAX_RETRIES retries"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build the JSON request body for Gemini API.
     */
    private fun buildRequestBody(
        userMessage: String,
        history: List<ConversationMessage>
    ): String {
        val contents = buildList {
            // Add conversation history
            history.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role)
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", msg.text)
                        })
                    }
                })
            }

            // Add current user message
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(buildJsonObject {
                        put("text", userMessage)
                    })
                }
            })
        }

        val requestJson = buildJsonObject {
            putJsonArray("contents") {
                contents.forEach { add(it) }
            }
            put("systemInstruction", buildJsonObject {
                putJsonArray("parts") {
                    add(buildJsonObject {
                        put("text", JarvisPersonality.getSystemPrompt())
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.7)
                put("topP", 0.9)
                put("topK", 40)
                put("maxOutputTokens", 300)
            })
            putJsonArray("safetySettings") {
                listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT").forEach { category ->
                    add(buildJsonObject {
                        put("category", category)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    })
                }
            }
        }

        return requestJson.toString()
    }

    /**
     * Extract the text content from Gemini's JSON response.
     */
    private fun extractTextFromResponse(responseBody: String): String {
        return try {
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val candidates = jsonResponse["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

            text ?: "I apologize, ${JarvisPersonality.userHonorific}. I wasn't able to process that."
        } catch (e: Exception) {
            "I encountered an error parsing the response, ${JarvisPersonality.userHonorific}. Could you repeat that?"
        }
    }

    /**
     * Quick check if the API key is valid by making a minimal request.
     */
    suspend fun validateApiKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = chat("Hello", emptyList())
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * A single message in the conversation history.
 */
data class ConversationMessage(
    val role: String,  // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
