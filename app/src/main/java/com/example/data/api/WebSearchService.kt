package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

object WebSearchService {

    private val client = OkHttpClient()

    /**
     * Search the web using DuckDuckGo Zero Click Info API (free, no key required)
     */
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val lowerQuery = query.lowercase()
            
            // Intercept Weather Queries for localized IP-based tracking
            if (lowerQuery.contains("weather") || lowerQuery.contains("temperature")) {
                try {
                    var location = ""
                    val words = lowerQuery.split(Regex("\\s+"))
                    val inIndex = words.indexOf("in")
                    if (inIndex != -1 && inIndex + 1 < words.size) {
                        location = words[inIndex + 1]
                    }
                    val url = if (location.isNotEmpty()) "https://wttr.in/$location?format=%l:+%C,+%t,+feels+like+%f.+Wind:+%w,+Humidity:+%h" 
                              else "https://wttr.in/?format=%l:+%C,+%t,+feels+like+%f.+Wind:+%w,+Humidity:+%h"
                              
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.string()?.let {
                            if (!it.contains("Unknown location") && it.isNotBlank()) {
                                return@withContext "Here is the local weather info da: $it"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSearchService", "Weather API failed: ${e.message}")
                }
            }

            // Use DuckDuckGo API - free and doesn't require API key
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&t=prithi"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    // Parse JSON response
                    parseSearchResult(body, query)
                } ?: "I couldn't find any information about that right now."
            } else {
                Log.e("WebSearchService", "Search failed: ${response.code}")
                "Network error while searching. Please try again."
            }
        } catch (e: Exception) {
            Log.e("WebSearchService", "Search exception: ${e.message}")
            "I had trouble searching online. Let me try to help you differently."
        }
    }

    /**
     * Simple JSON response parser for DuckDuckGo API
     */
    private fun parseSearchResult(jsonBody: String, query: String): String {
        return try {
            // Extract abstract/definition from DuckDuckGo response
            val abstractPattern = Pattern.compile("\"AbstractText\":\"([^\"]+)\"")
            val abstractMatcher = abstractPattern.matcher(jsonBody)

            if (abstractMatcher.find()) {
                val abstract = abstractMatcher.group(1)
                    ?.replace("\\u003c", "<")
                    ?.replace("\\u003e", ">")
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("\\\\", "")
                    ?.trim() ?: ""

                if (abstract.isNotEmpty()) {
                    return "Here's what I found about '$query': $abstract"
                }
            }

            val answerPattern = Pattern.compile("\"Answer\":\"([^\"]+)\"")
            val answerMatcher = answerPattern.matcher(jsonBody)
            if (answerMatcher.find()) {
                val answer = answerMatcher.group(1)
                    ?.replace("\\u003c", "<")
                    ?.replace("\\u003e", ">")
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("\\\\", "")
                    ?.trim() ?: ""
                if (answer.isNotEmpty()) {
                    return "I found this for '$query': $answer"
                }
            }
            val definitionPattern = Pattern.compile("\"Definition\":\"([^\"]+)\"")
            val definitionMatcher = definitionPattern.matcher(jsonBody)
            if (definitionMatcher.find()) {
                val definition = definitionMatcher.group(1)
                    ?.replace("\\u003c", "<")
                    ?.replace("\\u003e", ">")
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("\\\\", "")
                    ?.trim() ?: ""
                if (definition.isNotEmpty()) {
                    return "Definition of '$query': $definition"
                }
            }
            val relatedPattern = Pattern.compile("\"RelatedTopics\":\\[(.*?)]", Pattern.DOTALL)
            val relatedMatcher = relatedPattern.matcher(jsonBody)

            if (relatedMatcher.find()) {
                val relatedStr = relatedMatcher.group(1) ?: ""
                val textPattern = Pattern.compile("\"Text\":\"([^\"]+)\"")
                val textMatcher = textPattern.matcher(relatedStr)
                if (textMatcher.find()) {
                    val topicText = textMatcher.group(1)
                        ?.replace("\\u003c", "<")
                        ?.replace("\\u003e", ">")
                        ?.replace("&quot;", "\"")
                        ?.replace("&amp;", "&")
                        ?.replace("\\\\", "")
                        ?.trim() ?: ""
                    if (topicText.isNotEmpty()) {
                        return "I found this related information about '$query': $topicText"
                    }
                }
            }

            val resultPattern = Pattern.compile("\"Results\":\\[(.*?)\\]", Pattern.DOTALL)
            val resultMatcher = resultPattern.matcher(jsonBody)
            if (resultMatcher.find()) {
                val resultStr = resultMatcher.group(1) ?: ""
                val textPattern = Pattern.compile("\"Text\":\"([^\"]+)\"")
                val textMatcher = textPattern.matcher(resultStr)
                if (textMatcher.find()) {
                    val resultText = textMatcher.group(1)
                        ?.replace("\\u003c", "<")
                        ?.replace("\\u003e", ">")
                        ?.replace("&quot;", "\"")
                        ?.replace("&amp;", "&")
                        ?.replace("\\\\", "")
                        ?.trim() ?: ""
                    if (resultText.isNotEmpty()) {
                        return "Here is a result for '$query': $resultText"
                    }
                }
            }

            "I couldn't find detailed information about '$query' right now, but I can open a browser search for more details."
        } catch (e: Exception) {
            Log.e("WebSearchService", "Parse error: ${e.message}")
            "I found some information but couldn't parse it properly. Try searching online for more details."
        }
    }

    /**
     * Generate a Google search URL for the user to open in browser
     */
    fun generateGoogleSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
    }

    /**
     * Generate a web search suggestion using common search engines
     */
    suspend fun getSuggestedResponse(userInput: String): String = withContext(Dispatchers.IO) {
        return@withContext if (shouldSearchWeb(userInput)) {
            try {
                val searchResult = searchWeb(userInput)
                searchResult
            } catch (e: Exception) {
                "I couldn't search online for that, but I can still help! Ask me something else or check the web directly."
            }
        } else {
            ""
        }
    }

    /**
     * Determine if a query should trigger web search
     */
    private fun shouldSearchWeb(query: String): Boolean {
        val searchTriggers = listOf(
            "who is", "what is", "how to", "what's", "whats", "tell me about",
            "search", "find", "lookup", "define", "definition", "meaning",
            "weather", "time", "news", "latest", "today", "now",
            "how do i", "how do you", "can you find", "search for",
            "information", "about", "explain", "teach me"
        )
        val lowerQuery = query.lowercase()
        return searchTriggers.any { lowerQuery.contains(it) }
    }
}
