package com.example.data.preference

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.preference.NotificationStatsManager

class PrithiNotificationService : NotificationListenerService() {
    private lateinit var statsManager: NotificationStatsManager

    override fun onCreate() {
        super.onCreate()
        statsManager = NotificationStatsManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.notification?.let { notification ->
            val category = notification.category
            if (category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_SOCIAL || notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null) {
                val sender = notification.extras.getString(Notification.EXTRA_TITLE) ?: "App"
                statsManager.logMessageReceived(sender)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // When a user dismisses or clicks a notification, we count it as "watched"
        statsManager.logNotificationWatched()
    }
}