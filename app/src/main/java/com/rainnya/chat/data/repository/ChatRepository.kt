package com.rainnya.chat.data.repository

import com.google.gson.Gson
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.MessageRole
import com.rainnya.chat.data.model.ChatSession
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.data.websocket.AstrBotWsClient
import com.rainnya.chat.data.websocket.WsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class ChatRepository(
    private val settings: AppSettings,
    private val wsClient: AstrBotWsClient = AstrBotWsClient(Gson()),
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var currentSessionId: String? = null
    private var currentMessageId: String? = null

    val wsEvents: Flow<WsEvent> = wsClient.events

    fun connect() {
        if (!settings.isConfigured) return
        _connectionState.value = ConnectionState.CONNECTING
        wsClient.connect(settings.wsUrl, settings.apiKey)
    }

    fun disconnect() {
        wsClient.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendMessage(text: String) {
        val messageId = UUID.randomUUID().toString()
        currentMessageId = messageId

        val userMsg = ChatMessage(
            id = messageId,
            content = text,
            role = MessageRole.USER,
            sessionId = currentSessionId ?: "",
        )
        _messages.value = _messages.value + userMsg

        wsClient.sendMessage(
            text = text,
            username = settings.username,
            sessionId = currentSessionId,
            messageId = messageId,
        )
    }

    fun handleWsEvent(event: WsEvent) {
        when (event) {
            is WsEvent.Connected -> {
                _connectionState.value = ConnectionState.CONNECTED
            }
            is WsEvent.Disconnected -> {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            is WsEvent.Error -> {
                _connectionState.value = ConnectionState.ERROR
            }
            is WsEvent.MessageReceived -> handleMessage(event)
        }
    }

    private fun handleMessage(event: WsEvent.MessageReceived) {
        val msg = event.msg
        when (msg.type) {
            "session_id" -> {
                msg.session_id?.let { sid ->
                    currentSessionId = sid
                }
            }
            "plain" -> {
                val text = msg.data?.toString() ?: ""
                if (text.isEmpty()) return
                val isStreaming = msg.streaming ?: true
                val messages = _messages.value
                val lastAssistant = messages.indexOfLast { it.role == MessageRole.ASSISTANT }

                if (isStreaming && lastAssistant >= 0 && messages[lastAssistant].streaming) {
                    val updated = messages.toMutableList()
                    updated[lastAssistant] = updated[lastAssistant].copy(
                        content = updated[lastAssistant].content + text
                    )
                    _messages.value = updated
                } else {
                    _messages.value = messages + ChatMessage(
                        id = msg.message_id ?: UUID.randomUUID().toString(),
                        content = text,
                        role = MessageRole.ASSISTANT,
                        sessionId = currentSessionId ?: "",
                        streaming = isStreaming,
                    )
                }
            }
            "end" -> {
                val messages = _messages.value
                val lastAssistant = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                if (lastAssistant >= 0) {
                    val updated = messages.toMutableList()
                    updated[lastAssistant] = updated[lastAssistant].copy(streaming = false)
                    _messages.value = updated
                }
            }
            "error" -> {
                val errText = msg.data?.toString() ?: msg.code ?: "Unknown error"
                _messages.value = _messages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Error: $errText",
                    role = MessageRole.SYSTEM,
                    sessionId = currentSessionId ?: "",
                )
            }
        }
    }

    fun newSession() {
        currentSessionId = null
        _messages.value = emptyList()
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
