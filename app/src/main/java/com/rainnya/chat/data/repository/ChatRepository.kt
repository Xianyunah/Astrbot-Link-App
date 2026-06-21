package com.rainnya.chat.data.repository

import android.util.Log
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

private const val TAG = "RainnyaRepo"

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
        if (!settings.isConfigured) {
            Log.w(TAG, "Cannot connect: settings not configured")
            return
        }
        Log.d(TAG, "Connecting...")
        _connectionState.value = ConnectionState.CONNECTING
        wsClient.connect(settings.wsUrl, settings.apiKey)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        wsClient.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendMessage(text: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send: not connected")
            return
        }
        val messageId = UUID.randomUUID().toString()
        Log.d(TAG, "Sending message id=$messageId text=$text")

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
        try {
            when (event) {
                is WsEvent.Connected -> {
                    Log.d(TAG, "Event: Connected")
                    _connectionState.value = ConnectionState.CONNECTED
                }
                is WsEvent.Disconnected -> {
                    Log.d(TAG, "Event: Disconnected")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                is WsEvent.Error -> {
                    Log.e(TAG, "Event: Error ${event.error}")
                    _connectionState.value = ConnectionState.ERROR
                }
                is WsEvent.MessageReceived -> handleMessage(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WS event: ${e.message}", e)
        }
    }

    private fun handleMessage(event: WsEvent.MessageReceived) {
        try {
            val msg = event.msg
            Log.d(TAG, "Handle message type=${msg.type} streaming=${msg.streaming}")

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
                    val currentList = _messages.value.toMutableList()
                    val lastAssistant = currentList.indexOfLast { it.role == MessageRole.ASSISTANT }

                    if (isStreaming && lastAssistant >= 0 && currentList[lastAssistant].streaming) {
                        currentList[lastAssistant] = currentList[lastAssistant].copy(
                            content = currentList[lastAssistant].content + text
                        )
                        _messages.value = currentList
                    } else {
                        currentList.add(
                            ChatMessage(
                                id = msg.message_id ?: UUID.randomUUID().toString(),
                                content = text,
                                role = MessageRole.ASSISTANT,
                                sessionId = currentSessionId ?: "",
                                streaming = isStreaming,
                            )
                        )
                        _messages.value = currentList
                    }
                }
                "end" -> {
                    val currentList = _messages.value.toMutableList()
                    val lastAssistant = currentList.indexOfLast { it.role == MessageRole.ASSISTANT }
                    if (lastAssistant >= 0) {
                        currentList[lastAssistant] = currentList[lastAssistant].copy(streaming = false)
                        _messages.value = currentList
                    }
                }
                "error" -> {
                    val errText = msg.data?.toString() ?: msg.code ?: "Unknown error"
                    Log.e(TAG, "Server error: $errText")
                    _messages.value = _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Error: $errText",
                        role = MessageRole.SYSTEM,
                        sessionId = currentSessionId ?: "",
                    )
                }
                "message_saved" -> Log.d(TAG, "Message saved: ${msg.data}")
                "agent_stats" -> Log.d(TAG, "Agent stats: ${msg.data}")
                else -> Log.d(TAG, "Unhandled message type: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}", e)
        }
    }

    fun switchSession(sessionId: String) {
        Log.d(TAG, "Switch to session $sessionId")
        currentSessionId = sessionId
        _messages.value = emptyList()
    }

    fun newSession() {
        Log.d(TAG, "New session")
        currentSessionId = null
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
