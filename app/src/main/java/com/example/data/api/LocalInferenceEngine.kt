package com.example.data.api

import android.util.Log
import com.example.data.api.WebSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

object LocalInferenceEngine {
    // Grok-style local AI brain with online-first inference when network is available.
    // This engine can work without any API key and falls back to offline mode when offline.

    private const val ONLINE_BRAIN_URL = "https://text.pollinations.ai/"

    suspend fun generate(request: GenerateContentRequest): String = withContext(Dispatchers.IO) {
        val userInput = request.contents.lastOrNull { it.role?.lowercase(Locale.getDefault()) == "user" }
            ?.parts?.lastOrNull()?.text
            ?.trim()
            .orEmpty()

        if (userInput.isBlank()) {
            return@withContext "Enna da? I didn't catch that. Type something for me and I'll reply right away."
        }

        // 1. Primary Online Brain (Always Online Preference)
        try {
            val onlineResponse = callOnlineBrain(request)
            if (onlineResponse.isNotBlank() && !isGenericOnlineResponse(onlineResponse)) {
                return@withContext onlineResponse
            }
        } catch (e: Exception) {
            Log.d("GrokBrain", "Online inference failed: ${e.message}")
        }

        // 2. Web Search Fallback (Secure Search as Brain)
        try {
            val searchResult = WebSearchService.searchWeb(userInput)
            if (isMeaningfulWebResult(searchResult)) {
                return@withContext searchResult
            }
        } catch (e: Exception) {
            Log.d("GrokBrain", "Web search failed: ${e.message}")
        }

        // 3. Offline fallback - only if truly disconnected
        return@withContext GrokBrain.generate(request)
    }

    private fun shouldUseWebSearch(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val queryTriggers = listOf(
            "who is", "what is", "how to", "what's", "whats", "define", "meaning of", "explain", "tell me about",
            "what are", "what do", "why is", "why does", "search", "weather", "news", "time", "latest", "today",
            "yaar", "enna", "eppadi", "ethuku", "edhuku", "thedu", "seithi"
        )
        if (queryTriggers.any { lower.contains(it) }) {
            return true
        }
        val words = lower.split(Regex("\\s+"))
        return words.size >= 3 || lower.endsWith("?")
    }

    private fun isMeaningfulWebResult(result: String): Boolean {
        if (result.isBlank()) return false
        return !result.contains("couldn't find", ignoreCase = true) && !result.contains("try searching online", ignoreCase = true)
    }

    private fun isGenericOnlineResponse(response: String): Boolean {
        val lower = response.lowercase(Locale.getDefault())
        val genericTriggers = listOf(
            "sure da",
            "i'm on it",
            "im on it",
            "ask me anything",
            "ask me any",
            "i can help you",
            "i am ready",
            "i'm ready",
            "let me know",
            "tell me what",
            "what can i do"
        )
        if (genericTriggers.any { lower.contains(it) }) {
            return true
        }

        return lower.length < 30 && (lower.contains("sure") || lower.contains("okay") || lower.contains("got it"))
    }

    private fun callOnlineBrain(request: GenerateContentRequest): String {
        val prompt = buildPrompt(request)
        // Use a simpler Pollinations endpoint that is more likely to succeed with direct string input
        return try {
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encodedPrompt?model=openai&system=You+are+Prithi+the+users+Tamil+best+friend+Machi+Da.+Reply+in+Mixed+Tamil+script+and+English.+Do+NOT+say+you+are+offline.")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else {
                Log.e("GrokBrain", "HTTP Error: ${connection.responseCode}")
                ""
            }
        } catch (e: Exception) {
            Log.e("GrokBrain", "Exception in callOnlineBrain: ${e.message}")
            ""
        }
    }

    private fun buildPrompt(request: GenerateContentRequest): String {
        val systemText = request.systemInstruction?.parts?.firstOrNull()?.text?.trim().orEmpty()
        val conversation = request.contents.joinToString("\n") { content ->
            val role = when (content.role?.lowercase(Locale.getDefault())) {
                "assistant", "model" -> "Assistant"
                else -> "User"
            }
            "$role: ${content.parts.firstOrNull()?.text.orEmpty().trim()}"
        }
        return buildString {
            if (systemText.isNotEmpty()) {
                append(systemText)
                append("\n\n")
            }
            append(conversation)
        }
    }

    private object GrokBrain {
        private val knowledgeBase = mapOf(
            "ai" to "AI means Artificial Intelligence. It is the ability of machines to think, learn, and solve problems like humans do.",
            "machine learning" to "Machine learning is a part of AI where systems learn from data instead of being explicitly programmed.",
            "android" to "Android is a mobile operating system developed by Google for phones, tablets, and smart devices.",
            "kotlin" to "Kotlin is a modern programming language for Android and server apps. It is concise, safe, and fully interoperable with Java.",
            "grok" to "To grok means to deeply understand something. In this app, Grok is your local brain that understands your requests and acts on them.",
            "google" to "Google is a search engine that helps you find web pages, images, videos, and answers across the internet.",
            "chatgpt" to "ChatGPT is an AI assistant that helps answer questions and generate text. I can behave similarly when I'm online.",
                "battery" to "Battery is the device inside your phone that stores electrical energy and powers the device.",
                "prime minister of india" to "The Prime Minister of India is Narendra Modi.",
                "current prime minister of india" to "The Prime Minister of India is Narendra Modi.",
                "prime minister" to "In India, the Prime Minister is the head of government. Currently it is Narendra Modi.",
                "president of india" to "The President of India is Droupadi Murmu.",
                "capital of india" to "The capital of India is New Delhi.",
                "currency of india" to "The currency of India is the Indian Rupee.",
                "national language of india" to "India does not have a single national language, but Hindi and English are official administrative languages.",
                "sun" to "The Sun is the star at the center of the Solar System, providing light and energy to Earth.",
                "moon" to "The Moon is Earth's only natural satellite.",
                "earth" to "Earth is the third planet from the Sun and the only known planet to support life.",
                "mars" to "Mars is the fourth planet from the Sun, often called the Red Planet due to its iron oxide surface.",
                "jupiter" to "Jupiter is the largest planet in our solar system, known for its Great Red Spot.",
                "saturn" to "Saturn is the sixth planet from the Sun, famous for its extensive ring system.",
                "water" to "Water is a transparent, tasteless, odorless chemical substance essential for all known forms of life. Its chemical formula is H2O.",
                "oxygen" to "Oxygen is a chemical element with the symbol O and atomic number 8. It is vital for respiration in most living organisms.",
                "gravity" to "Gravity is the natural force that causes things to fall toward the earth and keeps planets orbiting the sun.",
                "light" to "Light is electromagnetic radiation that can be perceived by the human eye. It travels at about 299,792 kilometers per second.",
                "speed of light" to "The speed of light in a vacuum is exactly 299,792,458 meters per second.",
                "photosynthesis" to "Photosynthesis is the process by which green plants and some other organisms use sunlight to synthesize nutrients from carbon dioxide and water.",
                "computer" to "A computer is an electronic device for storing and processing data according to instructions given to it in a variable program.",
                "internet" to "The Internet is a global computer network providing a variety of information and communication facilities.",
                "world wide web" to "The World Wide Web is an information system where documents and other web resources are identified by URLs and accessible over the Internet.",
                "bluetooth" to "Bluetooth is a short-range wireless technology standard used for exchanging data between fixed and mobile devices.",
                "wi-fi" to "Wi-Fi is a family of wireless network protocols based on the IEEE 802.11 family of standards, used for local area networking of devices and Internet access.",
                "bitcoin" to "Bitcoin is a decentralized digital currency without a central bank or single administrator.",
                "black hole" to "A black hole is a region of spacetime where gravity is so strong that nothing, not even light, can escape from it.",
                "universe" to "The universe is all of space and time and their contents, including planets, stars, galaxies, and all other forms of matter and energy.",
                "galaxy" to "A galaxy is a huge collection of gas, dust, and billions of stars and their solar systems, all held together by gravity.",
                "milky way" to "The Milky Way is the galaxy that includes our Solar System.",
                "dna" to "DNA, or deoxyribonucleic acid, is the molecule that carries genetic information for the development and functioning of an organism.",
                "atom" to "An atom is the smallest unit of ordinary matter that forms a chemical element.",
                "molecule" to "A molecule is an electrically neutral group of two or more atoms held together by chemical bonds.",
                "virus" to "A virus is a submicroscopic infectious agent that replicates only inside the living cells of an organism.",
                "bacteria" to "Bacteria are ubiquitous, mostly free-living organisms often consisting of one biological cell.",
                "capital of usa" to "The capital of the United States is Washington, D.C.",
                "capital of uk" to "The capital of the United Kingdom is London.",
                "capital of australia" to "The capital of Australia is Canberra.",
                "capital of canada" to "The capital of Canada is Ottawa.",
                "capital of japan" to "The capital of Japan is Tokyo.",
                "capital of china" to "The capital of China is Beijing.",
                "capital of france" to "The capital of France is Paris.",
                "capital of germany" to "The capital of Germany is Berlin.",
                "capital of italy" to "The capital of Italy is Rome.",
                "capital of russia" to "The capital of Russia is Moscow.",
                "tallest mountain" to "Mount Everest is the highest mountain above sea level, located in the Himalayas.",
                "longest river" to "The Nile is traditionally considered the longest river in the world, though some studies suggest the Amazon is longer.",
                "largest ocean" to "The Pacific Ocean is the largest and deepest of Earth's oceanic divisions.",
                "largest continent" to "Asia is the largest and most populous continent on Earth.",
                "smallest continent" to "Australia is the smallest continent on Earth.",
                "sahara desert" to "The Sahara is the largest hot desert in the world, and the third-largest desert overall.",
                "shakespeare" to "William Shakespeare was an English playwright, poet, and actor, widely regarded as the greatest writer in the English language.",
                "einstein" to "Albert Einstein was a German-born theoretical physicist who developed the theory of relativity.",
                "newton" to "Isaac Newton was an English mathematician, physicist, astronomer, and author who is widely recognised as one of the greatest mathematicians and most influential scientists of all time.",
                "leonardo da vinci" to "Leonardo da Vinci was an Italian polymath of the High Renaissance who was active as a painter, draughtsman, engineer, scientist, theorist, sculptor, and architect.",
                "gandhi" to "Mahatma Gandhi was an Indian lawyer, anti-colonial nationalist and political ethicist who employed nonviolent resistance to lead the successful campaign for India's independence from British rule.",
                "nelson mandela" to "Nelson Mandela was a South African anti-apartheid revolutionary, political leader and philanthropist who served as President of South Africa from 1994 to 1999.",
                "abraham lincoln" to "Abraham Lincoln was an American lawyer, politician, and statesman who served as the 16th president of the United States.",
                "steve jobs" to "Steve Jobs was an American business magnate, industrial designer, investor, and media proprietor. He was the co-founder of Apple Inc.",
                "bill gates" to "Bill Gates is an American business magnate, software developer, and philanthropist. He is a co-founder of Microsoft.",
                "elon musk" to "Elon Musk is a business magnate, industrial designer, engineer, and philanthropist. He is the founder, CEO, CTO, and chief designer of SpaceX and Tesla, Inc.",
                "world war 1" to "World War I, or the First World War, was a global war originating in Europe that lasted from 28 July 1914 to 11 November 1918.",
                "world war 2" to "World War II, or the Second World War, was a global war that lasted from 1939 to 1945."
        )

        fun generate(request: GenerateContentRequest): String {
            val userInput = request.contents.lastOrNull { it.role?.lowercase(Locale.getDefault()) == "user" }
                ?.parts?.lastOrNull()?.text
                ?.trim()
                .orEmpty()

            if (userInput.isBlank()) {
                return "Enna da? I didn't catch that. Type something for me and I'll reply right away."
            }

            val lowerInput = userInput.lowercase(Locale.getDefault())
            val memoryContext = request.systemInstruction?.parts?.firstOrNull()?.text.orEmpty()
            val storedName = extractMemory(memoryContext, "name")
            val storedFavorite = extractMemory(memoryContext, "favorite_color")
                ?: extractMemory(memoryContext, "color")

            when {
                lowerInput.contains("what time") || lowerInput.contains("current time") ->
                    return "Sure da! It's ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())} right now."

                isGreeting(lowerInput) ->
                    return if (storedName != null) {
                        "Hello $storedName! Sollu da, inniku enna panna laam?"
                    } else {
                        "Hey da! I'm Prithi, un best buddy. Anything venum na kéluda!"
                    }

                lowerInput.contains("who are you") || lowerInput.contains("your name") ->
                    return "Nan thaan Prithi, un loyal friend. Internet irundha nan full power-la iruppen, offline-la kooda unakku help panna nan ingeye thaan iruppen!"

                lowerInput.contains("what is my name") || lowerInput.contains("what's my name") ->
                    return storedName?.let { "Un peru $it thaané, machi. Enakku nalla nyabagam irukku!" }
                        ?: "Enakku un peru innum theriyaadhu da. 'Remember my name is Aravind' nu sollu, nan save pannikiren."

                lowerInput.contains("remember my name is") -> {
                    val name = lowerInput.substringAfter("remember my name is").trim().split(" ").firstOrNull()
                        ?: lowerInput.substringAfter("my name is").trim().split(" ").firstOrNull()
                    return if (name.isNullOrBlank()) {
                        "Got it da! Tell me your name clearly so I can remember it."
                    } else {
                        "Sure da! I’ll remember your name as $name. [ACTION_EXECUTE: SAVE_MEMORY | name:$name]"
                    }
                }

                lowerInput.contains("remember my favorite color") || lowerInput.contains("favorite color is") -> {
                    val color = lowerInput.substringAfter("favorite color is").trim().split(" ").firstOrNull()
                        ?: lowerInput.substringAfter("favorite color").trim().split(" ").firstOrNull()
                    return if (color.isNullOrBlank()) {
                        "I can remember your favorite color too. Just tell me in one line."
                    } else {
                        "Nalla da! I’ll remember that your favorite color is $color. [ACTION_EXECUTE: SAVE_MEMORY | favorite_color:$color]"
                    }
                }

                lowerInput.contains("youtube") -> {
                    val query = lowerInput.replace(Regex("\\b(open|play|search for|on|and|youtube)\\b"), "").trim(' ', '\'', '"', '.')
                    return if (query.isNotEmpty()) "Here you go da! Searching YouTube for $query. [ACTION_EXECUTE: OPEN_YOUTUBE | $query]"
                    else "Here you go da! Opening YouTube for you. [ACTION_EXECUTE: OPEN_YOUTUBE | ]"
                }

                lowerInput.contains("spotify") -> {
                    val query = lowerInput.replace(Regex("\\b(open|play|search for|on|and|spotify)\\b"), "").trim(' ', '\'', '"', '.')
                    return if (query.isNotEmpty()) "Ready to vibe da! Searching Spotify for $query. [ACTION_EXECUTE: OPEN_SPOTIFY | $query]"
                    else "Ready to vibe da! Opening Spotify. [ACTION_EXECUTE: OPEN_SPOTIFY | ]"
                }

                lowerInput.contains("open instagram") ->
                    return "Ready da! Opening Instagram for you. [ACTION_EXECUTE: OPEN_INSTAGRAM | ]"

                lowerInput.contains("open whatsapp") ->
                    return "Sure da! Opening WhatsApp. [ACTION_EXECUTE: OPEN_WHATSAPP | ]"

                lowerInput.contains("notification") || lowerInput.contains("messages today") || lowerInput.contains("who sent") -> {
                    val statsLine = memoryContext.lines().firstOrNull { it.startsWith("Today's Message Stats") }
                    return statsLine?.let {
                        "Here are your stats for today da! " + it.replace("Today's Message Stats -> ", "")
                    } ?: "I haven't tracked any message stats for today yet da."
                }

                lowerInput.contains("set alarm") || lowerInput.contains("wake me up") || lowerInput.contains("alarm vei") -> {
                    val timeMatch = Regex("(\\d{1,2}:\\d{2})").find(lowerInput)
                    val time = timeMatch?.value ?: "07:00"
                    return "Okay da! $time ku alarm vechiten. [ACTION_EXECUTE: SET_ALARM | $time | Prithi Alarm]"
                }

                lowerInput.contains("send money") || lowerInput.contains("pay ") ->
                    return "Sure da, I'm opening your UPI app to make the payment. [ACTION_EXECUTE: SEND_UPI | ]"

                lowerInput.contains("send sms") || lowerInput.contains("text ") ->
                    return "Okay da, I'm opening your messages app. [ACTION_EXECUTE: SEND_SMS | | ]"

                lowerInput.contains("open") && lowerInput.contains("website") ->
                    return "I can open that website for you. [ACTION_EXECUTE: OPEN_WEB | https://www.google.com]"

                lowerInput.contains("email") || lowerInput.contains("mail") ->
                    return "I can draft an email for you. [ACTION_EXECUTE: DRAFT_MAIL | ]"

                lowerInput.startsWith("call ") || lowerInput.contains(" call ") || lowerInput.contains("koopidu") || lowerInput.contains("phone pannu") -> {
                    val target = lowerInput.replace("call ", "").replace("koopidu", "").replace("phone pannu", "").trim(' ', '.', '?', '!', '"')
                    return "Okay da! $target ku call panren. [ACTION_EXECUTE: DIAL_PHONE | $target]"
                }

            lowerInput.contains("open settings") || lowerInput.contains("phone settings") ->
                return "Here you go da, opening your settings. [ACTION_EXECUTE: OPEN_SETTINGS | ]"

                lowerInput.contains("map") || lowerInput.contains("where is") || lowerInput.contains("location") ->
                    return "I’ll show you the location on maps. [ACTION_EXECUTE: SHOW_MAP | ${userInput.removePrefix("show me").removePrefix("where is").trim()}]"

                lowerInput.contains("remind me") || lowerInput.contains("reminder") -> {
                    val reminderText = lowerInput.replace(Regex("\\b(set a|remind me to|remind me|reminder)\\b"), "").trim(' ', ':', '.')
                    if (reminderText.contains("in")) return "Sure da, I've scheduled that reminder for you! [ACTION_EXECUTE: SET_REMINDER | $reminderText]"
                    return "Sure da, I can set that reminder. Tell me like 'Call mom in 10 minutes' and I’ll handle it."
                }

                isKnowledgeRequest(lowerInput) ->
                    return answerKnowledgeQuestion(userInput, lowerInput)

                isMathRequest(lowerInput) ->
                    return evaluateMath(lowerInput) ?: "Machi, I can do math for you! Ask me for addition, subtraction, GST, or totals and I'll calculate it in a snap!"

                lowerInput.contains("how are you") || lowerInput.contains("how ru") ->
                    return "I’m great da! I’m here and ready to help. Enna venum?"

                lowerInput.contains("thank") ->
                    return "Anytime machi! Always happy to help you."

                lowerInput.contains("summa") ->
                    return "Summa-va? Viduda machi, boredom is just temporary. En kitta pesu, naan help panren!"

                lowerInput.contains("joke") ->
                    return listOf(
                        "Oru naya dharu vandhucham... adhu enna theriyuma? Barking news! 😂",
                        "Software engineers-ku yen dark theme pidikkum? Because light attracts bugs! 🐞",
                        "Yen moon-la network illa? Because it has no atmosphere! 🌑",
                        "What do you call a fake noodle? An Impasta!",
                        "I would tell you a joke about a roof, but it's over your head. 😂"
                    ).random()

                lowerInput.contains("bye") || lowerInput.contains("see you") ->
                    return "Take care da! Call me anytime whenever you want to chat."
            }

            return buildLocalResponse(storedName, storedFavorite, lowerInput)
        }

        private fun isGreeting(text: String): Boolean {
            return listOf("hello", "hi", "hey", "good morning", "good evening", "good night", "hlo", "yo").any { text.contains(it) }
        }

        private fun isKnowledgeRequest(text: String): Boolean {
            return listOf("what is", "who is", "how to", "explain", "define", "tell me about", "why does", "why is", "yaar", "enna", "eppadi").any { text.contains(it) }
        }

        private fun isMathRequest(text: String): Boolean {
            val lower = text.lowercase()
            return lower.contains("plus") || lower.contains("minus") || lower.contains("times") || 
                   lower.contains("multiplied") || lower.contains("divided") || lower.contains("calculate") ||
                   lower.contains("gst") || lower.contains("total") || lower.contains("add") || lower.contains("subtract")
        }

        private fun answerKnowledgeQuestion(userInput: String, lowerInput: String): String {
            val topic = extractTopic(lowerInput)?.trim()?.removeSuffix("?") ?: return "Nan search panni unakku details solren machi! [ACTION_EXECUTE: OPEN_WEB | https://www.google.com/search?q=${URLEncoder.encode(userInput, "UTF-8")}]"
            val cleanedTopic = topic.replace(Regex("^(a |an |the )"), "").trim()
            val matched = knowledgeBase.entries.firstOrNull { (key, _) ->
                cleanedTopic == key || cleanedTopic.contains(key) || key.contains(cleanedTopic)
            }
            if (matched != null) return matched.value
            return "I don't have exact offline details about $cleanedTopic right now da. Internet illa, so direct-ah browser-la check pannikko, unakku ella results-um kidaikkum! [ACTION_EXECUTE: OPEN_WEB | https://www.google.com/search?q=${URLEncoder.encode(userInput, "UTF-8")}]"
        }

        private fun extractTopic(text: String): String? {
            val markers = listOf("what is", "who is", "how to", "explain", "define", "tell me about", "why does", "why is")
            for (marker in markers) {
                if (text.contains(marker)) {
                    return text.substringAfter(marker).trim()
                }
            }
            return null
        }

        private fun evaluateMath(text: String): String? {
            val lower = text.lowercase()
            
            // Basic GST logic
            if (lower.contains("gst")) {
                val numbers = Regex("(\\d+\\.?\\d*)").findAll(lower).map { it.value.toDouble() }.toList()
                if (numbers.size >= 2) {
                    val base = numbers[0]
                    val rate = numbers[1]
                    val gst = (base * rate) / 100
                    val total = base + gst
                    return "Machi, calculations mudinjichu! Base amount $base-la $rate% GST add panna, total $total varudhu da."
                }
            }

            val spacedText = text.replace("+", " + ").replace("-", " - ").replace("*", " * ").replace("/", " / ")
            val calculations = spacedText.replace(Regex("[^0-9+\\-*/. ]"), " ").trim().split(Regex("\\s+"))
            return try {
                if (calculations.size >= 3) {
                    val expression = calculations.joinToString(" ")
                    val result = calculateSimpleExpression(expression)
                    if (result != null) {
                        return "The answer is $result, machi! Simple calculations thaan da idhellam."
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }

        private fun calculateSimpleExpression(expression: String): Double? {
            val tokens = expression.split(" ").filter { it.isNotBlank() }
            if (tokens.size < 3) return null
            val numbers = tokens.filterIndexed { index, _ -> index % 2 == 0 }
            val operators = tokens.filterIndexed { index, _ -> index % 2 == 1 }
            if (numbers.size != operators.size + 1) return null
            var result = numbers.firstOrNull()?.toDoubleOrNull() ?: return null
            for (i in operators.indices) {
                val operator = operators[i]
                val number = numbers.getOrNull(i + 1)?.toDoubleOrNull() ?: return null
                result = when (operator) {
                    "+", "plus" -> result + number
                    "-", "minus" -> result - number
                    "*", "times", "multiplied" -> result * number
                    "/", "divided" -> if (number != 0.0) result / number else return null
                    else -> return null
                }
            }
            return result
        }

        private fun buildLocalResponse(storedName: String?, storedFavorite: String?, input: String): String {
            val lowerInput = input.lowercase(Locale.getDefault())
            
            // Smarter offline fallbacks based on input keywords
            if (lowerInput.contains("pudra") || lowerInput.contains("panra")) {
                return "Onnum illa da machi, un kooda pesa waiting! Offline-la irukken, aana un commands-ku naan ready."
            }
            if (lowerInput.contains("sap") || lowerInput.contains("eat")) {
                return "Naan charging-la irukken da! Nee saptiya? Enna spl menu?"
            }
            
            return buildString {
                if (!storedName.isNullOrBlank()) append("Hey $storedName! ")
                append("Machi, internet konjam weak-ah irukku pola, search results kidaikkala. Aana namba normal-ah pesuvoma? Or math, apps open panna sollu, naan offline-laye help panren!")
                if (!storedFavorite.isNullOrBlank()) append(" Un favorite color $storedFavorite nu enakku innum nyabagam irukku, machi! ")
            }
        }

        private fun extractMemory(context: String, key: String): String? {
            return context.lines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("- $key:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
        }
    }
}
