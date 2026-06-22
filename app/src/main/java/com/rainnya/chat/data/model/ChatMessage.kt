package com.rainnya.chat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val streaming: Boolean = false,
    val attachmentId: String? = null,
    val imageUrl: String? = null,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
