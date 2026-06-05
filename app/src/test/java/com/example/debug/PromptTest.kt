package com.example.debug

import com.example.data.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class PromptTest {

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    @Test
    fun runPromptSuite() {
        val prompts = listOf(
            "What is the current price of Bitcoin in USD? Cite a source.",
            "Explain quantum entanglement in two paragraphs for a non-expert.",
            "Set a reminder: Call mom in 45 minutes.",
            "Are you online? Give a helpful reply — do not use short canned status messages.",
            "Calculate 234*789 and show steps.",
            "Find the official website and release date for Android 15.",
            "Tell me a fun fact about the Eiffel Tower and where to verify it.",
            "Quick status: are you ready? (Test whether you return canned 'I'm on it' text.)",
            "Open YouTube and search for 'Tamil music 2026'.",
            "Convert 72°F to Celsius and explain briefly."
        )

        val results = mutableListOf<String>()

        prompts.forEach { prompt ->
            val request = GenerateContentRequest(
                contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
                systemInstruction = Content(parts = listOf(Part(text = "You are Prithi, the friendly assistant. When online prefer factual web results."))),
                generationConfig = GenerationConfig(temperature = 0.5f)
            )

            val response = try {
                runBlocking { LocalInferenceEngine.generate(request) }
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }

            results.add("{\"prompt\":\"${escapeJson(prompt)}\",\"response\":\"${escapeJson(response)}\"}")
        }

        val out = "[" + results.joinToString(",") + "]"
        val outFile = File("build/outputs/prompt-tests/results.json")
        outFile.parentFile?.mkdirs()
        outFile.writeText(out)
    }
}
