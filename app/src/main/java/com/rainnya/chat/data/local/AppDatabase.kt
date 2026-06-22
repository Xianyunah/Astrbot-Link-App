package com.rainnya.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.ChatSession

@Database(entities = [ChatSession::class, ChatMessage::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?:                 Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rainnya_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
