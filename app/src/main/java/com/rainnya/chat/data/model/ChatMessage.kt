package com.rainnya.chat.data.model

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val streaming: Boolean = false,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
