package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "user" or "prithi"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String, // e.g., "user_name", "user_mood", "favorite_topic"
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateTime: Long, // epoch millis
    val category: String, // "Personal", "Work", "Task", "Health"
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_logs")
data class AutomationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val actionType: String, // "OPEN_WEB", "DRAFT_MAIL", "DIAL_PHONE", "SHOW_MAP", "SET_REMINDER", "AI_SEARCH"
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    val parameters: String = "" // Optional metadata in json format
)
