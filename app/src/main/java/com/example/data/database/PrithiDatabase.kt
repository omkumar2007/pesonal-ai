package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AutomationLog
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.Reminder

@Database(
    entities = [
        ChatMessage::class,
        MemoryItem::class,
        Reminder::class,
        AutomationLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrithiDatabase : RoomDatabase() {

    abstract fun prithiDao(): PrithiDao

    companion object {
        @Volatile
        private var INSTANCE: PrithiDatabase? = null

        fun getDatabase(context: Context): PrithiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrithiDatabase::class.java,
                    "prithi_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
