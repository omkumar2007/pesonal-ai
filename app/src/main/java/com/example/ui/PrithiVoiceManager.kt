package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class PrithiVoiceManager(
    private val context: Context,
    private val onInitSuccess: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private var isListening = false
    private var isWakeWordListening = false
    private var currentLanguage: String = "ta"
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Initialize Text To Speech
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("PrithiVoiceManager", "Error initializing TTS: ${e.message}")
        }
    }

    fun updateLanguage(language: String) {
        currentLanguage = language
        if (isTtsInitialized) {
            val locale = when (language) {
                "ta" -> Locale("ta", "IN")
                else -> Locale.US
            }
            tts?.language = locale
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val tamilLocale = Locale("ta", "IN")
            val isTamilAvailable = tts?.isLanguageAvailable(tamilLocale)
            val result = if (isTamilAvailable == TextToSpeech.LANG_AVAILABLE ||
                isTamilAvailable == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                isTamilAvailable == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                tts?.setLanguage(tamilLocale)
            } else {
                tts?.setLanguage(Locale.getDefault())
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            // Set natural pitch and speed for a natural human best friend voice
            tts?.setPitch(1.02f)
            tts?.setSpeechRate(1.0f)
            
            // Try to set a high-quality human/natural voice if available
            try {
                val voices = tts?.voices
                if (!voices.isNullOrEmpty()) {
                    val bestVoice = voices.filter { 
                        it.locale?.language == "ta" 
                    }.maxByOrNull { 
                        var score = 0
                        if (it.name.contains("female", ignoreCase = true)) score += 10
                        if (it.isNetworkConnectionRequired) score += 5 // Network voices sound more human
                        score
                    }
                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                    }
                }
            } catch (e: Exception) {
                Log.d("PrithiVoiceManager", "Error selecting custom voice: ${e.message}")
            }

            isTtsInitialized = true
            onInitSuccess()
        } else {
            Log.e("PrithiVoiceManager", "TTS Initialization failed")
        }
    }

    private fun cleanTextForSpeech(text: String): String {
        // 1. Strip Action and URL tags
        var cleaned = text.replace(Regex("\\[ACTION_EXECUTE:[^]]*]"), "")
        cleaned = cleaned.replace(Regex("https?://\\S+"), "")
        cleaned = cleaned.replace(Regex("[*_#`]+"), "")

        // 2. Filter out Emojis & other miscellaneous non-speaking symbols
        val sb = StringBuilder()
        var i = 0
        val len = cleaned.length
        while (i < len) {
            val codePoint = cleaned.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val type = Character.getType(codePoint).toByte()

            val isEmojiOrSymbol = when (type) {
                Character.OTHER_SYMBOL,
                Character.MODIFIER_SYMBOL,
                Character.SURROGATE -> true
                else -> false
            } || (codePoint in 0x1F000..0x1FAFF) || (codePoint in 0x2600..0x27BF)

            if (!isEmojiOrSymbol) {
                sb.append(cleaned.substring(i, i + charCount))
            }
            i += charCount
        }

        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun speak(text: String) {
        if (isTtsInitialized) {
            val cleanedText = cleanTextForSpeech(text)
            if (cleanedText.isNotEmpty()) {
                tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "PRITHI_TTS_ID")
            }
        }
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStarted: () -> Unit = {},
        onListeningStopped: () -> Unit = {}
    ) {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListeningStarted()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    onListeningStopped()
                }

                override fun onError(error: Int) {
                    isListening = false
                    onListeningStopped()
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, please try again"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
                        else -> "Speech recognizer error: $error"
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    } else {
                        onError("Could not recognize speech")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Voice command is always in English as requested
            val language = "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            onListeningStopped()
            onError("Failed to start speech recognizer: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    fun startWakeWordListening(onWakeWordDetected: () -> Unit) {
        if (!isTtsInitialized || isListening) return
        isWakeWordListening = true

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (isWakeWordListening) {
                        mainHandler.postDelayed({
                            if (isWakeWordListening) startWakeWordListening(onWakeWordDetected)
                        }, 2000)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.getOrNull(0)?.lowercase() ?: ""
                    if (text.contains("hey prithi") || text.contains("hi prithi") || text.contains("hey pretty")) {
                        isWakeWordListening = false
                        onWakeWordDetected()
                    } else if (isWakeWordListening) {
                        startWakeWordListening(onWakeWordDetected)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // English hotword matching is more robust
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isWakeWordListening = false
        }
    }

    fun stopWakeWordListening() {
        isWakeWordListening = false
        speechRecognizer?.cancel()
    }

    fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
