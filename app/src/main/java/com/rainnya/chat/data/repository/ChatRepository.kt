package com.rainnya.chat.data.repository

import com.google.gson.Gson
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.MessageRole
import com.rainnya.chat.data.model.ChatSession
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.data.websocket.AstrBotWsClient
import com.rainnya.chat.data.websocket.WsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChatRepository(
    private val settings: AppSettings,
    private val wsClient: AstrBotWsClient = AstrBotWsClient(Gson()),
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var currentSessionId: String? = null

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
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val messageId = UUID.randomUUID().toString()

        val userMsg = ChatMessage(
            id = messageId,
            content = text,
            role = MessageRole.USER,
            sessionId = currentSessionId ?: "",
        )
        _messages.value = _messages.value + userMsg

        wsClient.sendMessage(
            text = text,
            username = settings.taggedUsername,
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
                    val existing = _sessions.value.any { it.sessionId == sid }
                    if (!existing) {
                        _sessions.value = _sessions.value + ChatSession(
                            sessionId = sid,
                            displayName = "会话 ${_sessions.value.size + 1}",
                        )
                    }
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

    fun switchSession(sessionId: String) {
        currentSessionId = sessionId
        _messages.value = emptyList()
    }

    fun newSession() {
        currentSessionId = null
        _messages.value = emptyList()
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext "请先填写服务器地址和 API Key"
        try {
            val request = Request.Builder()
                .url("${settings.httpBaseUrl}/api/v1/chat/sessions?page=1&page_size=1")
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()
            val response = httpClient.newCall(request).execute()
            when (response.code) {
                200 -> "连接成功 ✓"
                401 -> "连接失败：API Key 无效"
                403 -> "连接失败：权限不足"
                404 -> "连接失败：接口路径错误"
                else -> "连接失败：HTTP ${response.code}"
            }
        } catch (e: java.net.ConnectException) {
            "连接失败：无法连接到服务器"
        } catch (e: java.net.SocketTimeoutException) {
            "连接失败：连接超时"
        } catch (e: Exception) {
            "连接失败：${e.localizedMessage ?: "未知错误"}"
        }
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
