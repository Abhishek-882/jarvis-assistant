package com.jarvis.assistant.ai

/**
 * Manages conversation context for multi-turn JARVIS conversations.
 *
 * Maintains a rolling window of messages to provide context to Gemini,
 * enabling natural multi-turn conversations like:
 * User: "What's the capital of France?"
 * JARVIS: "Paris, Sir."
 * User: "What's its population?"  ← Gemini understands "its" = Paris from context
 */
class ConversationManager(
    private val maxHistorySize: Int = 20
) {
    private val _history = mutableListOf<ConversationMessage>()

    /**
     * Get the current conversation history for Gemini API calls.
     */
    val history: List<ConversationMessage>
        get() = _history.toList()

    /**
     * Add a user message to the conversation.
     */
    fun addUserMessage(text: String) {
        _history.add(ConversationMessage(role = "user", text = text))
        trimHistory()
    }

    /**
     * Add JARVIS's response to the conversation.
     */
    fun addAssistantMessage(text: String) {
        _history.add(ConversationMessage(role = "model", text = text))
        trimHistory()
    }

    /**
     * Clear all conversation history (fresh start).
     */
    fun clearHistory() {
        _history.clear()
    }

    /**
     * Get the last N messages.
     */
    fun getRecentMessages(count: Int): List<ConversationMessage> {
        return _history.takeLast(count)
    }

    /**
     * Get the message count.
     */
    val messageCount: Int
        get() = _history.size

    /**
     * Trim history to keep within the max window size.
     * Always keeps pairs (user + model) to maintain coherence.
     */
    private fun trimHistory() {
        while (_history.size > maxHistorySize) {
            // Remove oldest pair
            if (_history.size >= 2) {
                _history.removeAt(0)
                _history.removeAt(0)
            } else {
                _history.removeAt(0)
            }
        }
    }
}
