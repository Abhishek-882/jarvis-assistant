package com.jarvis.assistant.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses JARVIS/Gemini responses to extract embedded action commands.
 *
 * Gemini responds with natural language + optional JSON action blocks
 * wrapped in ```action``` fences. This parser extracts both.
 */
object CommandParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val actionBlockRegex = Regex(
        """```action\s*\n?\s*(\{[^}]+\})\s*\n?\s*```""",
        RegexOption.DOT_MATCHES_ALL
    )

    /**
     * Parse a Gemini response into spoken text + optional action.
     */
    fun parse(response: String): ParsedResponse {
        val actionMatch = actionBlockRegex.find(response)

        val spokenText = response
            .replace(actionBlockRegex, "")
            .trim()
            .trimEnd('\n')

        val action = actionMatch?.let { match ->
            try {
                val jsonStr = match.groupValues[1].trim()
                parseAction(jsonStr)
            } catch (e: Exception) {
                null
            }
        }

        return ParsedResponse(
            spokenText = spokenText.ifBlank { "Done, ${JarvisPersonality.userHonorific}." },
            action = action
        )
    }

    /**
     * Parse a JSON string into a JarvisAction.
     */
    private fun parseAction(jsonStr: String): JarvisAction? {
        return try {
            val rawAction = json.decodeFromString<RawAction>(jsonStr)
            when (rawAction.action) {
                "open_app" -> JarvisAction.OpenApp(rawAction.app_name ?: return null)
                "set_alarm" -> JarvisAction.SetAlarm(
                    hour = rawAction.hour ?: return null,
                    minute = rawAction.minute ?: 0,
                    message = rawAction.message
                )
                "make_call" -> JarvisAction.MakeCall(rawAction.contact ?: return null)
                "send_sms" -> JarvisAction.SendSms(
                    contact = rawAction.contact ?: return null,
                    message = rawAction.message ?: return null
                )
                "toggle_flashlight" -> JarvisAction.ToggleFlashlight(
                    on = rawAction.state?.lowercase() == "on"
                )
                "set_volume" -> JarvisAction.SetVolume(rawAction.level ?: 50)
                "web_search" -> JarvisAction.WebSearch(rawAction.query ?: return null)
                "set_timer" -> JarvisAction.SetTimer(
                    minutes = rawAction.minutes ?: return null,
                    message = rawAction.message
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attempt to parse a user command locally (offline) without AI.
     * Uses simple pattern matching for common commands.
     */
    fun parseOfflineCommand(text: String): JarvisAction? {
        val lower = text.lowercase().trim()

        return when {
            // Alarm patterns
            lower.matches(Regex(""".*set (?:an )?alarm (?:for |at )?(\d{1,2}):?(\d{2})?\s*(am|pm)?.*""")) -> {
                val match = Regex("""(\d{1,2}):?(\d{2})?\s*(am|pm)?""").find(lower)
                match?.let {
                    var hour = it.groupValues[1].toIntOrNull() ?: return null
                    val minute = it.groupValues[2].toIntOrNull() ?: 0
                    val period = it.groupValues[3]
                    if (period == "pm" && hour < 12) hour += 12
                    if (period == "am" && hour == 12) hour = 0
                    JarvisAction.SetAlarm(hour, minute, null)
                }
            }

            // Open app patterns
            lower.startsWith("open ") -> {
                val appName = lower.removePrefix("open ").trim()
                JarvisAction.OpenApp(appName)
            }
            lower.startsWith("launch ") -> {
                val appName = lower.removePrefix("launch ").trim()
                JarvisAction.OpenApp(appName)
            }

            // Call patterns
            lower.startsWith("call ") -> {
                val contact = lower.removePrefix("call ").trim()
                JarvisAction.MakeCall(contact)
            }

            // Flashlight
            lower.contains("flashlight on") || lower.contains("torch on") ||
            lower.contains("turn on") && lower.contains("flash") -> {
                JarvisAction.ToggleFlashlight(true)
            }
            lower.contains("flashlight off") || lower.contains("torch off") ||
            lower.contains("turn off") && lower.contains("flash") -> {
                JarvisAction.ToggleFlashlight(false)
            }

            // Volume
            lower.contains("volume up") || lower.contains("louder") -> {
                JarvisAction.SetVolume(80)
            }
            lower.contains("volume down") || lower.contains("quieter") -> {
                JarvisAction.SetVolume(30)
            }
            lower.contains("mute") || lower.contains("silent") -> {
                JarvisAction.SetVolume(0)
            }

            else -> null
        }
    }
}

/**
 * Parsed response from JARVIS/Gemini.
 */
data class ParsedResponse(
    val spokenText: String,
    val action: JarvisAction?
)

/**
 * Actions that JARVIS can execute on the phone.
 */
sealed class JarvisAction {
    data class OpenApp(val appName: String) : JarvisAction()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String?) : JarvisAction()
    data class MakeCall(val contact: String) : JarvisAction()
    data class SendSms(val contact: String, val message: String) : JarvisAction()
    data class ToggleFlashlight(val on: Boolean) : JarvisAction()
    data class SetVolume(val level: Int) : JarvisAction()
    data class WebSearch(val query: String) : JarvisAction()
    data class SetTimer(val minutes: Int, val message: String?) : JarvisAction()
}

/**
 * Internal raw JSON deserialization model for Gemini action blocks.
 */
@Serializable
private data class RawAction(
    val action: String = "",
    val app_name: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val message: String? = null,
    val contact: String? = null,
    val state: String? = null,
    val level: Int? = null,
    val query: String? = null,
    val minutes: Int? = null
)
