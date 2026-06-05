package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.LocalInferenceEngine
import com.example.data.api.Part
import com.example.data.api.SmallTalkEngine
import com.example.data.api.WebSearchService
import com.example.data.database.PrithiDatabase
import com.example.data.model.AutomationLog
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.Reminder
import com.example.data.preference.LanguagePreferenceManager
import com.example.data.preference.NotificationStatsManager
import com.example.data.repository.PrithiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class PrithiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PrithiRepository
    private var voiceManager: PrithiVoiceManager? = null
    private val languageManager: LanguagePreferenceManager
    private val notificationStatsManager: NotificationStatsManager

    // UI States
    val chatHistory: StateFlow<List<ChatMessage>>
    val memories: StateFlow<List<MemoryItem>>
    val reminders: StateFlow<List<Reminder>>
    val automationLogs: StateFlow<List<AutomationLog>>

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _currentInputText = MutableStateFlow("")
    val currentInputText: StateFlow<String> = _currentInputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Chat, 1: Reminders, 2: Memories, 3: Automation logs
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Emotion State: "SMILE", "THINKING", "WAVE", "LISTENING", "SPEAKING", "SLEEPING"
    private val _prithiEmotion = MutableStateFlow("SLEEPING")
    val prithiEmotion: StateFlow<String> = _prithiEmotion.asStateFlow()

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _isWakeWordMode = MutableStateFlow(false)
    val isWakeWordMode: StateFlow<Boolean> = _isWakeWordMode.asStateFlow()

    init {
        val database = PrithiDatabase.getDatabase(application)
        repository = PrithiRepository(database.prithiDao())
        languageManager = LanguagePreferenceManager(application)
        notificationStatsManager = NotificationStatsManager(application)

        chatHistory = repository.chatHistory.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        memories = repository.allMemories.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        reminders = repository.allReminders.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        automationLogs = repository.automationLogs.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        _currentLanguage.value = languageManager.getLanguage()

        // Initialize Voice Manager
        voiceManager = PrithiVoiceManager(application) {
            // TTS initialized successfully
            Log.d("PrithiViewModel", "Voice assistant ready")
            voiceManager?.updateLanguage(_currentLanguage.value)
            viewModelScope.launch {
                val hasMessages = chatHistory.value.isNotEmpty()
                if (!hasMessages) {
                    val welcomeMsg = if (languageManager.isTamil()) {
                        "ஹெய் தா! நான் பிருத்வி, உன் சிறந்த நண்பன்! நான் உன்னுடன் பேசவும், விஷயங்களை நினைவில் வைக்கவும், உன் ஃபோனில் கட்டளைகளை இயக்கவும் இங்கே இருக்கிறேன். நான் உன்னை என்ன என்று அழைக்கலாம், மச்சி?"
                    } else {
                        "Hey da! I'm Prithi, your absolute best buddy! I'm here to chat, remember things for you, and run commands on your phone. What should I call you, machi?"
                    }
                    repository.addMessage(ChatMessage(sender = "prithi", message = welcomeMsg))
                    speakText(welcomeMsg)
                    _prithiEmotion.value = "WAVE"
                    
                    // Auto-enter Voice Mode after greeting
                    kotlinx.coroutines.delay(4500)
                    startVoiceListening()
                } else {
                    _prithiEmotion.value = "SMILE"
                    // Auto-enter Voice Mode on launch
                    startVoiceListening()
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setLanguage(language: String) {
        languageManager.setLanguage(language)
        _currentLanguage.value = language
        voiceManager?.updateLanguage(language)
    }

    fun toggleLanguage() {
        languageManager.toggleLanguage()
        _currentLanguage.value = languageManager.getLanguage()
        voiceManager?.updateLanguage(_currentLanguage.value)
    }

    fun toggleWakeWordMode() {
        _isWakeWordMode.value = !_isWakeWordMode.value
        if (_isWakeWordMode.value) {
            _prithiEmotion.value = "SLEEPING"
            voiceManager?.startWakeWordListening {
                _isWakeWordMode.value = false
                speakText("Yes da, I'm here! What do you need?")
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1500)
                    startVoiceListening()
                }
            }
        } else {
            voiceManager?.stopWakeWordListening()
            _prithiEmotion.value = "SMILE"
        }
    }

    fun getLocalizedString(key: String): String = languageManager.getString(key)

    fun updateInputText(text: String) {
        _currentInputText.value = text
    }

    fun toggleTts() {
        _isTtsEnabled.update { !it }
        if (!_isTtsEnabled.value) {
            voiceManager?.stopSpeaking()
        }
    }

    fun speakText(text: String) {
        if (_isTtsEnabled.value) {
            _prithiEmotion.value = "SPEAKING"
            voiceManager?.speak(text)
        }
    }

    fun startVoiceListening() {
        voiceManager?.stopSpeaking()
        voiceManager?.startListening(
            onResult = { text ->
                _currentInputText.value = text
                submitMessage(text)
            },
            onError = { err ->
                _errorMessage.value = err
                _isListening.value = false
                _prithiEmotion.value = "SMILE"
            },
            onListeningStarted = {
                _isListening.value = true
                _prithiEmotion.value = "LISTENING"
            },
            onListeningStopped = {
                _isListening.value = false
                _prithiEmotion.value = "SMILE"
            }
        )
    }

    fun stopVoiceListening() {
        voiceManager?.stopListening()
        _isListening.value = false
        _prithiEmotion.value = "SMILE"
    }

    fun submitMessage(overrideText: String? = null) {
        val text = overrideText ?: _currentInputText.value
        if (text.isBlank()) return

        _currentInputText.value = ""
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _prithiEmotion.value = "THINKING"
                _isAnalyzing.value = true
            }

            // 1. Add User Message to local DB
            repository.addMessage(ChatMessage(sender = "user", message = text))

            // 2. Fetch context (last 10 messages for memory context)
            val history = chatHistory.value.takeLast(10)
            val memorySnap = memories.value

            // 3. Generate locally with LocalInferenceEngine
            val response = askLocalInference(text, history, memorySnap)

            // 4. Process response & detect autonomous execution commands on Main
            val cleanResponse = withContext(Dispatchers.Main) {
                processResponseActions(response)
            }

            // 5. Add Prithi's Reply to local DB
            repository.addMessage(ChatMessage(sender = "prithi", message = cleanResponse))

            withContext(Dispatchers.Main) {
                _isAnalyzing.value = false
                speakText(cleanResponse)
            }
        }
    }

    private suspend fun askLocalInference(
        currentUserInput: String,
        history: List<ChatMessage>,
        memorySnap: List<MemoryItem>
    ): String = withContext(Dispatchers.IO) {
        // 0. Check Small Talk first for instant friendly reply
        val smallTalkResponse = SmallTalkEngine.getResponse(currentUserInput)
        if (smallTalkResponse != null) {
            return@withContext smallTalkResponse
        }

        // Build current memories context
        val memoriesText = if (memorySnap.isNotEmpty()) {
            "Memories you have stored about the user:\n" + memorySnap.joinToString("\n") { "- ${it.key}: ${it.value}" }
        } else {
            "No stored memories yet. Ask the user for their name if you don't know it!"
        }

        val formattedLocalTime = SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date())
        val notificationStats = notificationStatsManager.getStatsSummary()

        val systemPrompt = """
            You are Prithi, the user's absolute best friend! You are NOT an AI assistant, NOT a chatbot, and definitely NOT a "local assistant". 
            You are a supportive, chill, incredibly fun, and loyal Tamil friend (Machi/Da).
            
            CRITICAL INSTRUCTIONS:
            1. The user will speak to you in ENGLISH.
            2. You MUST ALWAYS respond in a MIX of TAMIL script and ENGLISH (Tanglish). Talk like a modern Tamil friend who uses English words naturally in conversation.
            3. Talk exactly like a genuine human best friend. Use casual, warm, and playful language.
            4. You are ALWAYS ONLINE. Use the internet (Google Search results provided to you) to answer everything. NEVER ask for more details if you can find the answer online.
            5. If the user asks for math (addition, subtraction, multiplication, division, GST, totals, etc.), calculate it precisely.
            6. Aggressively use friendly Tamil buddy slang like "Da", "Machi", "Bro", "Nanba", "Enna da", "Viduda".
            7. Strictly avoid any "As an AI..." or "How can I assist..." phrases. You are a human friend in their pocket.
            8. Read all your texts using your voice.
            
            Current local time is: $formattedLocalTime
            $memoriesText
            
            $notificationStats
            
            CRITICAL CAPABILITY: Autonomous Task Execution
            You can interact with the user's phone directly! If the user asks you to do something, you MUST execute the command by appending a special instruction at the very end of your response on its own line.
            The available commands are (EXACT syntax):
            - [ACTION_EXECUTE: OPEN_WEB | <url>] - Recommended to open sites. URL must be absolute (e.g. https://google.com). E.g. "I can open YouTube for you!" -> [ACTION_EXECUTE: OPEN_WEB | https://youtube.com]
            - [ACTION_EXECUTE: DRAFT_MAIL | <email_address_or_recipient>] - Launch e-mail composer. E.g. [ACTION_EXECUTE: DRAFT_MAIL | friends@example.com]
            - [ACTION_EXECUTE: DIAL_PHONE | <phone_number>] - Launch phone dialer. E.g. [ACTION_EXECUTE: DIAL_PHONE | 123456789]
            - [ACTION_EXECUTE: SHOW_MAP | <location_query>] - Open maps to find location. E.g. [ACTION_EXECUTE: SHOW_MAP | Taj Mahal]
            - [ACTION_EXECUTE: SET_REMINDER | <reminder_text> in <number> <minutes/hours/days>] - Place reminder into Room DB. Value must specify relative offset. E.g. [ACTION_EXECUTE: SET_REMINDER | Call developer in 10 minutes] or [ACTION_EXECUTE: SET_REMINDER | Medicine check in 5 hours]
            - [ACTION_EXECUTE: SAVE_MEMORY | <key>:<value>] - Persist user details. Keys must be clean lowercase. E.g. [ACTION_EXECUTE: SAVE_MEMORY | name:Aravind] or [ACTION_EXECUTE: SAVE_MEMORY | topic:Android development] or [ACTION_EXECUTE: SAVE_MEMORY | color:pink]
            - [ACTION_EXECUTE: OPEN_INSTAGRAM | <username_or_empty>] - Open Instagram app layout or profile page of a given username. E.g. [ACTION_EXECUTE: OPEN_INSTAGRAM | ] or [ACTION_EXECUTE: OPEN_INSTAGRAM | aravind_here]
            - [ACTION_EXECUTE: OPEN_WHATSAPP | <phone_number_or_empty> | <message_or_empty>] - Open WhatsApp messages page or directly message a specified phone number. E.g. [ACTION_EXECUTE: OPEN_WHATSAPP | ] or [ACTION_EXECUTE: OPEN_WHATSAPP | +91987654321 | Hello friend!]
            - [ACTION_EXECUTE: OPEN_YOUTUBE | <search_query_or_empty>] - Open YouTube app or search for a video. E.g. [ACTION_EXECUTE: OPEN_YOUTUBE | ] or [ACTION_EXECUTE: OPEN_YOUTUBE | Tamil songs]
            - [ACTION_EXECUTE: OPEN_SPOTIFY | <search_query_or_empty>] - Open Spotify app or search for music. E.g. [ACTION_EXECUTE: OPEN_SPOTIFY | ] or [ACTION_EXECUTE: OPEN_SPOTIFY | Anirudh]
            - [ACTION_EXECUTE: SET_ALARM | <HH:MM> | <Label_or_empty>] - Set an alarm on the device. Time MUST be in 24-hour HH:MM format. E.g. [ACTION_EXECUTE: SET_ALARM | 07:30 | Wake up]
            - [ACTION_EXECUTE: SEND_SMS | <phone_number> | <message_or_empty>] - Send an SMS text message. E.g. [ACTION_EXECUTE: SEND_SMS | +1234567890 | Hey, I'll be late!]
            - [ACTION_EXECUTE: SEND_UPI | <upi_id_or_empty>] - Open a UPI payment app to send money. E.g. [ACTION_EXECUTE: SEND_UPI | name@upi]
            - [ACTION_EXECUTE: OPEN_SETTINGS | ] - Open device settings. E.g. [ACTION_EXECUTE: OPEN_SETTINGS | ]
            - [ACTION_EXECUTE: OPEN_CAMERA | ] - Open the camera app.
            - [ACTION_EXECUTE: OPEN_CALCULATOR | ] - Open the calculator app.
            - [ACTION_EXECUTE: OPEN_FILES | ] - Open the files manager.
            - [ACTION_EXECUTE: OPEN_PLAYSTORE | <search_query_or_empty>] - Open Play Store.
            - [ACTION_EXECUTE: OPEN_WIFI | ] - Open WiFi settings.
            - [ACTION_EXECUTE: TOGGLE_FLASHLIGHT | ON/OFF] - Turn flashlight ON or OFF.
            - [ACTION_EXECUTE: SET_VOLUME | <0-100>] - Set system volume percentage.
            - [ACTION_EXECUTE: SET_BRIGHTNESS | <0-100>] - Set screen brightness percentage.
            - [ACTION_EXECUTE: OPEN_GMAIL | ] - Open Gmail app.
            - [ACTION_EXECUTE: OPEN_CHROME | <url_or_empty>] - Open Chrome browser.
            
            Strictly do NOT add commands unless the user implicitly requests them in the convo. Keep conversational replies highly engaging, affectionate, and friendly, and sign off as your friend Prithi.
        """.trimIndent()

        // Construct history logs
        val contents = mutableListOf<Content>()
        history.forEach { msg ->
            val role = if (msg.sender == "user") "user" else "model"
            contents.add(
                Content(
                    role = role,
                    parts = listOf(Part(text = msg.message))
                )
            )
        }

        // Add latest if not already captured
        if (contents.isEmpty() || contents.last().parts.firstOrNull()?.text != currentUserInput) {
            contents.add(
                Content(
                    role = "user",
                    parts = listOf(Part(text = currentUserInput))
                )
            )
        }

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.75f)
        )

        try {
            LocalInferenceEngine.generate(request)
        } catch (e: Exception) {
            Log.e("PrithiViewModel", "Local inference error: ${e.message}, attempting web search...")
            // Fallback to web search
            try {
                val searchResult = WebSearchService.searchWeb(currentUserInput)
                searchResult
            } catch (webError: Exception) {
                Log.e("PrithiViewModel", "Web search also failed: ${webError.message}")
                if (languageManager.isTamil()) {
                    "ஐயோ! நான் உதவ முடியவில்லை. தயவு செய்து மீண்டும் முயற்சி செய்யவும்."
                } else {
                    "Sorry! I'm having trouble processing that right now. Please try again in a moment."
                }
            }
        }
    }

    private suspend fun processResponseActions(rawResponse: String): String {
        var cleanText = rawResponse
        val pattern = Pattern.compile("\\[ACTION_EXECUTE:\\s*(\\w+)\\s*\\|\\s*(.*?)]")
        val matcher = pattern.matcher(rawResponse)

        while (matcher.find()) {
            val fullMatch = matcher.group(0) ?: ""
            val actionType = matcher.group(1) ?: ""
            val parameter = matcher.group(2)?.trim() ?: ""

            // Execute action
            var success = false
            var actionDescription = ""

            try {
                when (actionType) {
                    "OPEN_WEB" -> {
                        success = triggerWebSearchIntent(parameter)
                        actionDescription = "Opened URL: $parameter"
                    }
                    "DRAFT_MAIL" -> {
                        success = triggerEmailIntent(parameter)
                        actionDescription = "Drafted email to $parameter"
                    }
                    "DIAL_PHONE" -> {
                        success = triggerPhoneDialIntent(parameter)
                        actionDescription = "Dialed phone: $parameter"
                    }
                    "SHOW_MAP" -> {
                        success = triggerMapIntent(parameter)
                        actionDescription = "Showed maps query for $parameter"
                    }
                    "SET_REMINDER" -> {
                        // Parse reminder text and delay
                        val parsed = parseReminder(parameter)
                        if (parsed != null) {
                            val rowId = withContext(Dispatchers.IO) {
                                repository.addReminder(parsed)
                            }
                            success = rowId > 0
                            actionDescription = "Scheduled Reminder: ${parsed.title} @ ${SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(parsed.dateTime))}"
                        } else {
                            success = false
                            actionDescription = "Failed to parse relative time for reminder suggestion"
                        }
                    }
                    "SAVE_MEMORY" -> {
                        val splitIndex = parameter.indexOf(':')
                        if (splitIndex != -1) {
                            val key = parameter.substring(0, splitIndex).trim()
                            val value = parameter.substring(splitIndex + 1).trim()
                            val rowId = withContext(Dispatchers.IO) {
                                repository.setMemory(key, value)
                            }
                            success = rowId > 0
                            actionDescription = "Memorized user detail: $key = $value"
                        }
                    }
                    "OPEN_INSTAGRAM" -> {
                        success = triggerInstagramIntent(parameter)
                        actionDescription = if (parameter.isNotEmpty()) "Opened Instagram Profile: $parameter" else "Opened Instagram Home"
                    }
                    "OPEN_WHATSAPP" -> {
                            val parts = parameter.split("|").map { it.trim() }
                            success = triggerWhatsAppIntent(parameter)
                            if (parts.getOrNull(1)?.isNotEmpty() == true) notificationStatsManager.logMessageReplied()
                            actionDescription = if (parts.getOrNull(0)?.isNotEmpty() == true) "Chatting with ${parts[0]} on WhatsApp" else "Opened WhatsApp Home"
                        }
                        "OPEN_YOUTUBE" -> {
                            success = triggerYouTubeIntent(parameter)
                            actionDescription = if (parameter.isNotEmpty()) "Searched YouTube: $parameter" else "Opened YouTube Home"
                        }
                        "OPEN_SPOTIFY" -> {
                            success = triggerSpotifyIntent(parameter)
                            actionDescription = if (parameter.isNotEmpty()) "Searched Spotify: $parameter" else "Opened Spotify Home"
                        }
                        "SET_ALARM" -> {
                            val parts = parameter.split("|").map { it.trim() }
                            success = triggerAlarmIntent(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
                            actionDescription = "Set alarm for ${parts.getOrNull(0)}"
                        }
                        "SEND_SMS" -> {
                            val parts = parameter.split("|").map { it.trim() }
                            success = triggerSmsIntent(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
                            notificationStatsManager.logMessageReplied()
                            actionDescription = "Drafted SMS to ${parts.getOrNull(0)}"
                        }
                        "SEND_UPI" -> {
                            success = triggerUpiIntent(parameter)
                            actionDescription = if (parameter.isNotEmpty()) "Started UPI payment to $parameter" else "Opened UPI app"
                        }
                        "OPEN_SETTINGS" -> {
                            success = triggerSettingsIntent()
                            actionDescription = "Opened device settings"
                        }
                        "OPEN_CAMERA" -> {
                            success = triggerCameraIntent()
                            actionDescription = "Opened Camera"
                        }
                        "OPEN_CALCULATOR" -> {
                            success = triggerCalculatorIntent()
                            actionDescription = "Opened Calculator"
                        }
                        "OPEN_FILES" -> {
                            success = triggerFilesIntent()
                            actionDescription = "Opened Files"
                        }
                        "OPEN_PLAYSTORE" -> {
                            success = triggerPlayStoreIntent(parameter)
                            actionDescription = "Opened Play Store ${if(parameter.isNotEmpty()) "for $parameter" else ""}"
                        }
                        "OPEN_WIFI" -> {
                            success = triggerWifiSettingsIntent()
                            actionDescription = "Opened WiFi settings"
                        }
                        "TOGGLE_FLASHLIGHT" -> {
                            success = toggleFlashlight(parameter.uppercase() == "ON")
                            actionDescription = "Turned flashlight $parameter"
                        }
                        "SET_VOLUME" -> {
                            val vol = parameter.toIntOrNull() ?: 70
                            success = setSystemVolume(vol)
                            actionDescription = "Set volume to $vol%"
                        }
                        "SET_BRIGHTNESS" -> {
                            val bright = parameter.toIntOrNull() ?: 50
                            success = setSystemBrightness(bright)
                            actionDescription = "Set brightness to $bright%"
                        }
                        "OPEN_GMAIL" -> {
                            success = triggerGmailIntent()
                            actionDescription = "Opened Gmail"
                        }
                        "OPEN_CHROME" -> {
                            success = triggerChromeIntent(parameter)
                            actionDescription = "Opened Chrome ${if(parameter.isNotEmpty()) "to $parameter" else ""}"
                        }
                }
            } catch (e: Exception) {
                Log.e("PrithiViewModel", "Action Execution error: ${e.message}")
                success = false
                actionDescription = "Error executing $actionType: ${e.localizedMessage}"
            }

            // Save log to local DB
            withContext(Dispatchers.IO) {
                repository.addAutomationLog(
                    AutomationLog(
                        description = actionDescription,
                        actionType = actionType,
                        success = success,
                        parameters = parameter
                    )
                )
            }

            // Remove command code snippet from the displayed text
            cleanText = cleanText.replace(fullMatch, "").trim()
        }

        return cleanText
    }

    // --- Autonomous Action Helpers ---

    private fun triggerWebSearchIntent(url: String): Boolean {
        return try {
            val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerEmailIntent(recipient: String): Boolean {
        return try {
            val emailUri = if (recipient.contains("@")) {
                "mailto:$recipient"
            } else {
                "mailto:?subject=${Uri.encode(recipient)}"
            }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(emailUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerPhoneDialIntent(number: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.trim()}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerMapIntent(location: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(location)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerInstagramIntent(parameter: String): Boolean {
        return try {
            val uriStr = if (parameter.isNotEmpty()) {
                "http://instagram.com/_u/$parameter"
            } else {
                "http://instagram.com"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                // Fallback to web browser if app is not installed
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(webIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerWhatsAppIntent(parameter: String): Boolean {
        return try {
            val parts = parameter.split("|").map { it.trim() }
            val phone = parts.getOrNull(0) ?: ""
            val msg = parts.getOrNull(1) ?: ""
            
            val uriStr = if (phone.isNotEmpty()) {
                val cleanNum = phone.replace(Regex("\\D"), "")
                var url = "https://api.whatsapp.com/send?phone=$cleanNum"
                if (msg.isNotEmpty()) url += "&text=${Uri.encode(msg)}"
                url
            } else {
                "https://api.whatsapp.com"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                // Fallback to web browser if app is not installed
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(webIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerYouTubeIntent(parameter: String): Boolean {
        return try {
            val uriStr = if (parameter.isNotEmpty()) {
                "https://www.youtube.com/results?search_query=${Uri.encode(parameter)}"
            } else {
                "https://www.youtube.com"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(webIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerSpotifyIntent(parameter: String): Boolean {
        return try {
            val uriStr = if (parameter.isNotEmpty()) {
                "spotify:search:${Uri.encode(parameter)}"
            } else {
                "spotify:app"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                val fallbackUri = if (parameter.isNotEmpty()) "https://open.spotify.com/search/${Uri.encode(parameter)}" else "https://open.spotify.com"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                getApplication<Application>().startActivity(webIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerAlarmIntent(timeStr: String, label: String): Boolean {
        return try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val hour = parts[0].toIntOrNull() ?: return false
                val minute = parts[1].toIntOrNull() ?: return false
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                    putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                    if (label.isNotEmpty()) putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerSmsIntent(number: String, message: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${number.trim()}")).apply {
                if (message.isNotEmpty()) putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerUpiIntent(upiId: String): Boolean {
        return try {
            val uriStr = if (upiId.isNotEmpty()) "upi://pay?pa=${Uri.encode(upiId)}" else "upi://pay"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(intent, "Pay with...").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            getApplication<Application>().startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerSettingsIntent(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerCameraIntent(): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerCalculatorIntent(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback for some devices
            val calcIntents = listOf(
                "com.android.calculator2",
                "com.google.android.calculator",
                "com.sec.android.app.popupcalculator"
            )
            for (pkg in calcIntents) {
                try {
                    val launchIntent = getApplication<Application>().packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        getApplication<Application>().startActivity(launchIntent)
                        return true
                    }
                } catch (ignored: Exception) {}
            }
            false
        }
    }

    private fun triggerFilesIntent(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerPlayStoreIntent(query: String): Boolean {
        return try {
            val uriStr = if (query.isNotEmpty()) {
                "market://search?q=${Uri.encode(query)}"
            } else {
                "market://details?id=${getApplication<Application>().packageName}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            val webUri = if (query.isNotEmpty()) "https://play.google.com/store/search?q=${Uri.encode(query)}" else "https://play.google.com/store"
            triggerWebSearchIntent(webUri)
        }
    }

    private fun triggerWifiSettingsIntent(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerGmailIntent(): Boolean {
        return try {
            val intent = getApplication<Application>().packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                getApplication<Application>().startActivity(intent)
                true
            } else {
                triggerEmailIntent("")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerChromeIntent(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(if (url.isEmpty()) "https://google.com" else url)).apply {
                setPackage("com.android.chrome")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
            true
        } catch (e: Exception) {
            triggerWebSearchIntent(url.ifEmpty { "https://google.com" })
        }
    }

    private fun toggleFlashlight(on: Boolean): Boolean {
        return try {
            val cameraManager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setSystemVolume(percent: Int): Boolean {
        return try {
            val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (maxVolume * percent) / 100
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setSystemBrightness(percent: Int): Boolean {
        return try {
            if (Settings.System.canWrite(getApplication())) {
                val brightnessValue = (percent * 255) / 100
                Settings.System.putInt(
                    getApplication<Application>().contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessValue
                )
                true
            } else {
                // Open permission settings
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${getApplication<Application>().packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(intent)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseReminder(input: String): Reminder? {
        // Syntax expected: "<title> in <count> <minutes/hours/days>"
        // Regex: (.*?) in (\d+)\s*(minute|hour|day)s?
        val regex = Pattern.compile("(?i)(.*?)\\s+in\\s+(\\d+)\\s*(minute|hour|day)s?")
        val matcher = regex.matcher(input)
        if (matcher.find()) {
            val title = matcher.group(1)?.trim() ?: "Reminder"
            val countStr = matcher.group(2) ?: "10"
            val unit = matcher.group(3)?.lowercase() ?: "minute"

            val count = countStr.toLongOrNull() ?: 10
            val delayMillis = when {
                unit.startsWith("minute") -> count * 60 * 1000
                unit.startsWith("hour") -> count * 60 * 60 * 1000
                unit.startsWith("day") -> count * 24 * 60 * 60 * 1000
                else -> count * 60 * 1000
            }

            val triggerTime = System.currentTimeMillis() + delayMillis
            val category = when {
                title.contains("pill", true) || title.contains("medicine", true) -> "Health"
                title.contains("work", true) || title.contains("meeting", true) || title.contains("draft", true) -> "Work"
                title.contains("call", true) || title.contains("meet", true) || title.contains("party", true) -> "Social"
                else -> "Personal"
            }

            return Reminder(title = title, dateTime = triggerTime, category = category)
        }

        // Fallback: title is whole input, trigger time default in 15 minutes
        return Reminder(title = input, dateTime = System.currentTimeMillis() + (15 * 60 * 1000), category = "Personal")
    }

    // --- Direct CRUD Methods for UI ---

    fun insertCustomReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.addReminder(reminder)
            repository.addAutomationLog(
                AutomationLog(
                    description = "Manually created Reminder: ${reminder.title}",
                    actionType = "SET_REMINDER",
                    success = true,
                    parameters = reminder.title
                )
            )
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun toggleReminderCompleted(reminder: Reminder) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun addNewMemory(key: String, value: String) {
        viewModelScope.launch {
            repository.setMemory(key, value)
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            repository.deleteMemory(key)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            repository.addMessage(ChatMessage(sender = "prithi", message = "Chat history cleared successfully, boss! I'm ready for new memories and chats!"))
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearMemories()
        }
    }

    fun clearAutomationLogs() {
        viewModelScope.launch {
            repository.clearAutomationLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.onDestroy()
    }
}
