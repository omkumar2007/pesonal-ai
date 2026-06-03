package com.example.data.api

object LocalInferenceEngine {
    suspend fun generate(request: GenerateContentRequest): String {
        // Placeholder local inference implementation.
        // Replace this with a real local model integration as needed.
        val userInput = request.contents.lastOrNull { it.role == "user" }
            ?.parts?.firstOrNull()?.text
            ?: "Hello!"

        return when {
            userInput.contains("remind me", ignoreCase = true) ->
                "Sure da! I can set that reminder for you. What time should I schedule it?"
            userInput.contains("remember", ignoreCase = true) || userInput.contains("save", ignoreCase = true) ->
                "Got it! I’ll remember that for you. Can you share the exact detail?"
            userInput.contains("open", ignoreCase = true) && userInput.contains("web", ignoreCase = true) ->
                "I’m ready to open that for you. Send me the link or name of the site."
            else ->
                "Hey da! I heard you say: '$userInput'. I’m thinking locally now, so I’ll respond as your buddy Prithi."
        }
    }
}
