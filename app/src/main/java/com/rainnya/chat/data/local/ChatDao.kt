package com.rainnya.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.ChatSession

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<ChatSession>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE chat_sessions SET displayName = :newName WHERE sessionId = :sessionId")
    suspend fun renameSession(sessionId: String, newName: String)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, updatedAt: Long)
}
