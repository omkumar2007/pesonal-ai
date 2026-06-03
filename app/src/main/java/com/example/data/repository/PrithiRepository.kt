package com.example.data.repository

import com.example.data.database.PrithiDao
import com.example.data.model.AutomationLog
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.Reminder
import kotlinx.coroutines.flow.Flow

class PrithiRepository(private val dao: PrithiDao) {

    val chatHistory: Flow<List<ChatMessage>> = dao.getChatHistory()
    val allMemories: Flow<List<MemoryItem>> = dao.getAllMemories()
    val allReminders: Flow<List<Reminder>> = dao.getAllReminders()
    val automationLogs: Flow<List<AutomationLog>> = dao.getAutomationLogs()

    suspend fun addMessage(message: ChatMessage): Long {
        return dao.insertMessage(message)
    }

    suspend fun clearHistory() {
        dao.clearChatHistory()
    }

    suspend fun getMemoryValue(key: String): String? {
        return dao.getMemoryByKey(key)?.value
    }

    suspend fun setMemory(key: String, value: String): Long {
        val existing = dao.getMemoryByKey(key)
        val memory = if (existing != null) {
            existing.copy(value = value, timestamp = System.currentTimeMillis())
        } else {
            MemoryItem(key = key, value = value)
        }
        return dao.insertMemory(memory)
    }

    suspend fun deleteMemory(key: String) {
        dao.deleteMemoryByKey(key)
    }

    suspend fun clearMemories() {
        dao.clearAllMemories()
    }

    suspend fun addReminder(reminder: Reminder): Long {
        return dao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: Reminder) {
        dao.updateReminder(reminder)
    }

    suspend fun deleteReminder(id: Long) {
        dao.deleteReminderById(id)
    }

    suspend fun addAutomationLog(log: AutomationLog): Long {
        return dao.insertAutomationLog(log)
    }

    suspend fun clearAutomationLogs() {
        dao.clearAutomationLogs()
    }
}
