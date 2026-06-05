package com.example.data.preference

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationStatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("PrithiNotificationStats", Context.MODE_PRIVATE)

    private fun checkAndResetDaily() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString("current_date", "")
        if (today != savedDate) {
            prefs.edit().clear().putString("current_date", today).apply()
        }
    }

    fun logMessageReceived(sender: String) {
        checkAndResetDaily()
        val total = prefs.getInt("total_received", 0) + 1
        
        val sendersJson = prefs.getString("senders_map", "{}")
        val json = JSONObject(sendersJson)
        val currentCount = json.optInt(sender, 0)
        json.put(sender, currentCount + 1)
        
        prefs.edit()
            .putInt("total_received", total)
            .putString("senders_map", json.toString())
            .apply()
    }

    fun logNotificationWatched() {
        checkAndResetDaily()
        val watched = prefs.getInt("total_watched", 0) + 1
        prefs.edit().putInt("total_watched", watched).apply()
    }

    fun logMessageReplied() {
        checkAndResetDaily()
        val replied = prefs.getInt("total_replied", 0) + 1
        prefs.edit().putInt("total_replied", replied).apply()
    }

    fun getStatsSummary(): String {
        checkAndResetDaily()
        val total = prefs.getInt("total_received", 0)
        val watched = prefs.getInt("total_watched", 0)
        val replied = prefs.getInt("total_replied", 0)
        
        val sendersJson = prefs.getString("senders_map", "{}")
        val json = JSONObject(sendersJson)
        var topSender = "Unknown"
        var maxCount = 0
        json.keys().forEach { key ->
            val count = json.getInt(key)
            if (count > maxCount) { maxCount = count; topSender = key }
        }
        return "Today's Message Stats -> Received: $total, Watched/Cleared: $watched, Replied: $replied, Top Sender: $topSender ($maxCount msgs)"
    }
}