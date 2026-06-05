package com.example.data.preference

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages language preferences (Tamil or English)
 */
class LanguagePreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PrithiLanguagePrefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_LANGUAGE = "language_preference"
        const val LANGUAGE_TAMIL = "ta"
        const val LANGUAGE_ENGLISH = "en"
    }

    fun setLanguage(language: String) {
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
    }

    fun getLanguage(): String {
        return prefs.getString(PREF_LANGUAGE, LANGUAGE_TAMIL) ?: LANGUAGE_TAMIL
    }

    fun isTamil(): Boolean = getLanguage() == LANGUAGE_TAMIL

    fun isEnglish(): Boolean = getLanguage() == LANGUAGE_ENGLISH

    fun toggleLanguage() {
        val current = getLanguage()
        val newLanguage = if (current == LANGUAGE_TAMIL) LANGUAGE_ENGLISH else LANGUAGE_TAMIL
        setLanguage(newLanguage)
    }

    /**
     * Get localized strings based on current language preference
     */
    fun getString(key: String): String {
        val language = getLanguage()
        return when (language) {
            LANGUAGE_TAMIL -> getTamilString(key)
            else -> getEnglishString(key)
        }
    }

    private fun getEnglishString(key: String): String {
        return when (key) {
            "welcome" -> "Hey! I'm Prithi, your absolute best buddy! I'm here to chat, remember things for you, and run commands on your phone. What should I call you?"
            "thinking" -> "Thinking..."
            "listening" -> "Listening..."
            "speaking" -> "Speaking..."
            "error_local" -> "I hit a processing error. Let me search online for that."
            "error_network" -> "Network error. Let me try again."
            "error_unknown" -> "Something went wrong. Please try again."
            "search_prefix" -> "Here's what I found about"
            "web_search_failed" -> "I couldn't search the web right now, but let me help you differently."
            "tab_chat" -> "Chat"
            "tab_reminders" -> "Reminders"
            "tab_memories" -> "Memories"
            "tab_logs" -> "Logs"
            "settings" -> "Settings"
            "language" -> "Language"
            "select_lang" -> "Select Language"
            "tamil" -> "Tamil"
            "english" -> "English"
            "clear_history" -> "Clear History"
            "send" -> "Send"
            "speak" -> "Speak"
            "stop_listening" -> "Stop Listening"
            else -> key
        }
    }

    private fun getTamilString(key: String): String {
        return when (key) {
            "welcome" -> "ஹெய் தா! நான் பிருத்வி, உன் சிறந்த நண்பன்! நான் உன்னுடன் பேசவும், விஷயங்களை நினைவில் வைக்கவும், உன் ஃபோனில் கட்டளைகளை இயக்கவும் இங்கே இருக்கிறேன். நான் உன்னை என்ன என்று அழைக்கலாம்?"
            "thinking" -> "சிந்திக்கிறேன்..."
            "listening" -> "கேட்டுக்கொண்டிருக்கிறேன்..."
            "speaking" -> "பேசிக்கொண்டிருக்கிறேன்..."
            "error_local" -> "ஒரு பிழை ஏற்பட்டது. அதை ஆன்லைனில் தேடிப் பார்க்கிறேன்."
            "error_network" -> "நெட்வொர்க் பிழை. மீண்டும் முயற்சி செய்கிறேன்."
            "error_unknown" -> "ஏதோ தவறு ஆனது. மீண்டும் முயற்சி செய்."
            "search_prefix" -> "இதைப் பற்றி நான் கண்டறைந்த தகவல்"
            "web_search_failed" -> "நான் ஆன்லைனில் தேட முடியவில்லை, ஆனால் வேறு வழியில் உதவ முடியும்."
            "tab_chat" -> "சேட்"
            "tab_reminders" -> "நினைப்பூட்டல்"
            "tab_memories" -> "நினைவுகள்"
            "tab_logs" -> "பதிவுகள்"
            "settings" -> "அமைப்புகள்"
            "language" -> "மொழி"
            "select_lang" -> "மொழியைத் தேர்ந்தெடுக்கவும்"
            "tamil" -> "தமிழ்"
            "english" -> "ஆங்கிலம்"
            "clear_history" -> "வரலாற்றை அழிக்கவும்"
            "send" -> "அனுப்பு"
            "speak" -> "பேசு"
            "stop_listening" -> "கேட்கப் பாயவேண்டாம்"
            else -> key
        }
    }
}
