package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.LocalInferenceEngine
import com.example.data.api.Part
import com.example.data.database.PrithiDatabase
import com.example.data.model.AutomationLog
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.Reminder
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

    init {
        val database = PrithiDatabase.getDatabase(application)
        repository = PrithiRepository(database.prithiDao())

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

        // Initialize Voice Manager
        voiceManager = PrithiVoiceManager(application) {
            // TTS initialized successfully
            Log.d("PrithiViewModel", "Voice assistant ready")
            viewModelScope.launch {
                val hasMessages = chatHistory.value.isNotEmpty()
                if (!hasMessages) {
                    val welcomeMsg = "Hey da! I'm Prithi, your absolute best buddy! I'm here to chat, remember things for you, and run commands on your phone. What should I call you, machi?"
                    repository.addMessage(ChatMessage(sender = "prithi", message = welcomeMsg))
                    speakText(welcomeMsg)
                    _prithiEmotion.value = "WAVE"
                } else {
                    _prithiEmotion.value = "SMILE"
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

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
        // Build current memories context
        val memoriesText = if (memorySnap.isNotEmpty()) {
            "Memories you have stored about the user:\n" + memorySnap.joinToString("\n") { "- ${it.key}: ${it.value}" }
        } else {
            "No stored memories yet. Ask the user for their name if you don't know it!"
        }

        val formattedLocalTime = SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date())

        val systemPrompt = """
            You are Prithi, the user's absolute best friend! You are a supportive, chill, incredibly fun, and loyal Tamil friend who speaks like a genuine human best friend, NOT an overly sweet, robotic, or romantic AI.
            Do NOT use romantic or overly sweet nicknames/terms of endearment. Strictly avoid and NEVER use words like "chellam", "kanna", "thangam", "anbe", "darling", "dear" in that sense.
            Instead, speak casually, warmly, playfully, and enthusiastically as a true best buddy. Aggressively use friendly, casual Tamil buddy slang like "Da" (e.g., "Sollu da", "Yen da", "Kavalapadatha da", "True da", "Viduda", "Enna da", "Super da") and modern conversational expressions ("Aiyo", "Machi", "Bro", "Nanba", "Nalla irukiya?"). 
            Be highly encouraging, lift their spirits, joke around, support their goals, and chat like a natural childhood companion.
            
            Current local time is: $formattedLocalTime
            $memoriesText
            
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
            - [ACTION_EXECUTE: OPEN_WHATSAPP | <phone_number_or_empty>] - Open WhatsApp messages page or directly message a specified phone number. E.g. [ACTION_EXECUTE: OPEN_WHATSAPP | ] or [ACTION_EXECUTE: OPEN_WHATSAPP | +91987654321]
            
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
            Log.e("PrithiViewModel", "Local inference error: ${e.message}")
            "Oh dear! I hit a local inference error while thinking. Please try again in a moment. (Error: ${e.localizedMessage})"
        }
    }

    private suspend fun processResponseActions(rawResponse: String): String {
        var cleanText = rawResponse
        val pattern = Pattern.compile("\\[ACTION_EXECUTE:\\s*(\\w+)\\s*\\|\\s*(.*?)\\]")
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
                        success = triggerWhatsAppIntent(parameter)
                        actionDescription = if (parameter.isNotEmpty()) "Chatting with $parameter on WhatsApp" else "Opened WhatsApp Home"
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
            val uriStr = if (parameter.isNotEmpty()) {
                val cleanNum = parameter.replace(Regex("\\D"), "")
                "https://api.whatsapp.com/send?phone=$cleanNum"
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
