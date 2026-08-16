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
    apiKey: String
) {
    companion object {
        private const val TAG = "JarvisGemini"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf("gemini-2.0-flash", "gemini-1.5-flash")
        private const val MAX_RETRIES = 2
    }

    val cleanApiKey: String = apiKey.trim()

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
        if (cleanApiKey.isBlank()) {
            return@withContext Result.failure(IOException("API key is missing or empty. Please set your Gemini API key in Settings."))
        }

        val requestBody = buildRequestBody(userMessage, conversationHistory)
        var lastError: Exception? = null

        // Try primary model, fallback to alternative if model not found / unavailable
        for (modelName in MODELS) {
            val url = "$BASE_URL/$modelName:generateContent?key=$cleanApiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .build()

            for (attempt in 0..MAX_RETRIES) {
                try {
                    val response = client.newCall(request).execute()

                    when (response.code) {
                        200 -> {
                            val responseBody = response.body?.string()
                                ?: return@withContext Result.failure(IOException("Empty response body from Gemini"))
                            val text = extractTextFromResponse(responseBody)
                            return@withContext Result.success(text)
                        }
                        429 -> {
                            if (attempt < MAX_RETRIES) {
                                kotlinx.coroutines.delay(1500L * (attempt + 1))
                                continue
                            }
                            return@withContext Result.failure(
                                IOException("Rate limit exceeded. Please wait a moment before asking again.")
                            )
                        }
                        400, 403 -> {
                            val errorBody = response.body?.string() ?: ""
                            val parsedMsg = parseErrorMessage(errorBody)
                            if (parsedMsg.contains("API key", ignoreCase = true) || parsedMsg.contains("API_KEY", ignoreCase = true)) {
                                return@withContext Result.failure(IOException("API key not valid. Please check your Gemini API key in Settings."))
                            }
                            // If it's a 400 not related to API key, log and return parsed message
                            return@withContext Result.failure(IOException(parsedMsg.ifBlank { "API Error ${response.code}" }))
                        }
                        404 -> {
                            // Model not found on this endpoint, try next model in loop
                            val errorBody = response.body?.string() ?: ""
                            lastError = IOException("Model $modelName unavailable: $errorBody")
                            break // Break inner retry, proceed to next model in MODELS loop
                        }
                        else -> {
                            val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                            val parsedMsg = parseErrorMessage(errorBody)
                            lastError = IOException(parsedMsg.ifBlank { "API error ${response.code}" })
                            if (attempt < MAX_RETRIES) {
                                kotlinx.coroutines.delay(1000L * (attempt + 1))
                            }
                        }
                    }
                } catch (e: IOException) {
                    lastError = e
                    if (attempt < MAX_RETRIES) {
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                    }
                } catch (e: Exception) {
                    lastError = e
                    break
                }
            }
        }

        Result.failure(lastError ?: IOException("Failed to connect to Gemini API"))
    }

    /**
     * Build the JSON request body for Gemini API with sanitized alternating turns.
     */
    private fun buildRequestBody(
        userMessage: String,
        history: List<ConversationMessage>
    ): String {
        // Sanitize history so that turns strictly alternate (user -> model -> user -> model)
        val validHistory = mutableListOf<ConversationMessage>()
        var expectedRole = "user"

        for (msg in history) {
            if (msg.text.isNotBlank()) {
                if (msg.role == expectedRole) {
                    validHistory.add(msg)
                    expectedRole = if (expectedRole == "user") "model" else "user"
                }
            }
        }

        // If the history ends with a user message, drop it so that the new userMessage is the user prompt
        if (validHistory.isNotEmpty() && validHistory.last().role == "user") {
            validHistory.removeAt(validHistory.lastIndex)
        }

        val contents = buildList {
            validHistory.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role)
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", msg.text)
                        })
                    }
                })
            }

            // Append current user message
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
                put("maxOutputTokens", 400)
            })
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

            text ?: "I am at your service, ${JarvisPersonality.userHonorific}."
        } catch (e: Exception) {
            "I received the response, ${JarvisPersonality.userHonorific}, but could not format it properly."
        }
    }

    /**
     * Parse structured error message from Gemini JSON error response.
     */
    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val root = json.parseToJsonElement(errorBody).jsonObject
            val errorObj = root["error"]?.jsonObject
            errorObj?.get("message")?.jsonPrimitive?.content ?: errorBody
        } catch (e: Exception) {
            errorBody
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
