package com.rainnya.chat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.rainnya.chat.data.local.AppDatabase
import com.rainnya.chat.data.model.ChatMessage
import com.rainnya.chat.data.model.MessageRole
import com.rainnya.chat.data.model.ChatSession
import com.rainnya.chat.data.model.WsMessageSegment
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.data.upload.ImageUploader
import com.rainnya.chat.data.websocket.AstrBotWsClient
import com.rainnya.chat.data.websocket.WsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "RainnyaRepo"

class ChatRepository(
    private val scope: CoroutineScope,
    private val context: Context,
    private val settings: AppSettings,
    private val wsClient: AstrBotWsClient = AstrBotWsClient(Gson()),
    private val imageUploader: ImageUploader = ImageUploader(),
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val db = AppDatabase.getInstance(context)
    private val dao = db.chatDao()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading

    private var currentSessionId: String? = null
    private val sessionMessages = mutableMapOf<String, MutableList<ChatMessage>>()
    private var streamTimeoutJob: Job? = null

    val wsEvents: Flow<WsEvent> = wsClient.events

    init {
        scope.launch(Dispatchers.IO) {
            loadFromDatabase()
        }
    }

    private suspend fun loadFromDatabase() {
        try {
            val sessions = dao.getAllSessions()
            _sessions.value = sessions
            for (session in sessions) {
                val msgs = dao.getMessagesForSession(session.sessionId)
                sessionMessages[session.sessionId] = msgs.toMutableList()
            }
            val latest = sessions.maxByOrNull { it.updatedAt }
            if (latest != null) {
                currentSessionId = latest.sessionId
                _messages.value = sessionMessages[latest.sessionId]?.toList() ?: emptyList()
                Log.d(TAG, "Loaded latest session: ${latest.sessionId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from database", e)
        }
    }

    fun connect() {
        if (!settings.isConfigured) {
            Log.w(TAG, "Cannot connect: settings not configured")
            return
        }
        Log.d(TAG, "Connecting...")
        _connectionState.value = ConnectionState.CONNECTING
        wsClient.connect(settings.wsUrl, settings.apiKey)
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        wsClient.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendMessage(text: String, attachmentId: String? = null) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send: not connected")
            return
        }
        ensureSessionExists()
        val messageId = UUID.randomUUID().toString()
        Log.d(TAG, "Sending message id=$messageId text=$text")

        val userMsgBuilder = ChatMessage(
            id = "user_$messageId",
            content = text,
            role = MessageRole.USER,
            sessionId = currentSessionId ?: "",
        )

        val list = _messages.value.toMutableList()
        list.add(userMsgBuilder)
        list.add(
            ChatMessage(
                id = "pending_$messageId",
                content = "",
                role = MessageRole.ASSISTANT,
                sessionId = currentSessionId ?: "",
                streaming = true,
            )
        )
        _messages.value = list
        saveCurrentMessages()

        val sendSessionId = if (currentSessionId?.startsWith("local_") == true) null else currentSessionId

        val messagePayload: Any = if (attachmentId != null) {
            val segments = mutableListOf<WsMessageSegment>()
            if (text.isNotBlank()) {
                segments.add(WsMessageSegment(type = "plain", text = text))
            }
            segments.add(WsMessageSegment(
                type = "image",
                attachment_id = attachmentId,
                url = "${settings.httpBaseUrl}/api/v1/file?attachment_id=$attachmentId",
            ))
            segments
        } else {
            text
        }

        wsClient.sendMessage(
            message = messagePayload,
            username = settings.taggedUsername,
            sessionId = sendSessionId,
            messageId = messageId,
        )
    }

    fun sendMessageWithImage(text: String, imageUri: Uri) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send: not connected")
            return
        }
        _uploading.value = true
        _uploadProgress.value = 0f

        scope.launch(Dispatchers.IO) {
            try {
                val attachmentId = imageUploader.upload(
                    context = context,
                    baseUrl = settings.httpBaseUrl,
                    apiKey = settings.apiKey,
                    uri = imageUri,
                    onProgress = { progress ->
                        _uploadProgress.value = progress
                    },
                )
                _uploadProgress.value = 1f
                _uploading.value = false
                sendMessage(text = text, attachmentId = attachmentId)
            } catch (e: Exception) {
                Log.e(TAG, "Image upload failed", e)
                _uploading.value = false
                _uploadProgress.value = 0f
            }
        }
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
                    markPendingStreamAsFailed()
                }
                is WsEvent.Error -> {
                    Log.e(TAG, "Event: Error ${event.error}")
                    _connectionState.value = ConnectionState.ERROR
                    markPendingStreamAsFailed()
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
            Log.d(TAG, "Handle message type=${msg.type}")

            when (msg.type) {
                "session_id" -> {
                    msg.session_id?.let { sid ->
                        val prevId = currentSessionId
                        currentSessionId = sid
                        val existing = _sessions.value.any { it.sessionId == sid }
                        if (!existing) {
                            val prevSession = prevId?.let { id ->
                                _sessions.value.find { it.sessionId == id }
                            }
                            val displayName = prevSession?.displayName
                                ?: "会话 ${_sessions.value.size + 1}"
                            val createdAt = prevSession?.createdAt
                                ?: System.currentTimeMillis()
                            val session = ChatSession(
                                sessionId = sid,
                                displayName = displayName,
                                createdAt = createdAt,
                            )
                            _sessions.value = _sessions.value.map {
                                if (it.sessionId == prevId) session else it
                            }
                            sessionMessages.remove(prevId)
                            sessionMessages[sid] = _messages.value.toMutableList()
                            runBlocking(Dispatchers.IO) {
                                try {
                                    if (prevId != null) {
                                        dao.deleteMessagesForSession(prevId)
                                        dao.deleteSession(prevId)
                                    }
                                    dao.insertSession(session)
                                    val msgs = _messages.value
                                    dao.deleteMessagesForSession(sid)
                                    dao.insertMessages(msgs)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to migrate session", e)
                                }
                            }
                        }
                    }
                }
                "plain" -> {
                    val text = msg.data?.toString() ?: ""
                    if (text.isEmpty()) return
                    val isStreaming = msg.streaming ?: true
                    val trimmed = text.trimStart()

                    if (trimmed.startsWith("{") && trimmed.contains("chatcmpl-tool-")) {
                        if (isStreaming) resetStreamTimeout()
                        return
                    }

                    val list = _messages.value.toMutableList()
                    val lastAssistant = list.indexOfLast { it.role == MessageRole.ASSISTANT }

                    if (isStreaming && lastAssistant >= 0 && list[lastAssistant].streaming) {
                        list[lastAssistant] = list[lastAssistant].copy(
                            content = list[lastAssistant].content + text
                        )
                        _messages.value = list
                        resetStreamTimeout()
                    } else {
                        list.add(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                content = text,
                                role = MessageRole.ASSISTANT,
                                sessionId = currentSessionId ?: "",
                                streaming = isStreaming,
                            )
                        )
                        _messages.value = list
                    }
                }
                "image" -> {
                    val list = _messages.value.toMutableList()
                    val attachmentId = msg.attachment_id
                    val imageUrl = msg.url

                    list.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = "",
                            role = MessageRole.ASSISTANT,
                            sessionId = currentSessionId ?: "",
                            streaming = false,
                            attachmentId = attachmentId,
                            imageUrl = imageUrl,
                        )
                    )
                    _messages.value = list
                    saveCurrentMessages()
                }
                "end" -> {
                    streamTimeoutJob?.cancel()
                    val list = _messages.value.toMutableList()
                    val lastAssistant = list.indexOfLast { it.role == MessageRole.ASSISTANT }
                    if (lastAssistant >= 0) {
                        list[lastAssistant] = list[lastAssistant].copy(streaming = false)
                        _messages.value = list
                    }
                    saveCurrentMessages()
                }
                "error" -> {
                    val errText = msg.data?.toString() ?: msg.code ?: "Unknown error"
                    Log.e(TAG, "Server error: $errText")
                    val list = _messages.value.toMutableList()
                    list.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = "Error: $errText",
                            role = MessageRole.SYSTEM,
                            sessionId = currentSessionId ?: "",
                        )
                    )
                    _messages.value = list
                }
                "message_saved" -> Log.d(TAG, "Message saved: ${msg.data}")
                "agent_stats" -> Log.d(TAG, "Agent stats: ${msg.data}")
                else -> Log.d(TAG, "Unhandled message type: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}", e)
        }
    }

    private fun resetStreamTimeout() {
        streamTimeoutJob?.cancel()
        streamTimeoutJob = scope.launch {
            delay(30_000L)
            val list = _messages.value.toMutableList()
            val lastAssistant = list.indexOfLast { it.role == MessageRole.ASSISTANT }
            if (lastAssistant >= 0 && list[lastAssistant].streaming) {
                list[lastAssistant] = list[lastAssistant].copy(streaming = false)
                _messages.value = list
                saveCurrentMessages()
                Log.d(TAG, "Stream timed out after 30s, ended streaming")
            }
        }
    }

    private fun saveCurrentMessages() {
        val sid = currentSessionId ?: return
        sessionMessages[sid] = _messages.value.toMutableList()
        runBlocking(Dispatchers.IO) {
            try {
                dao.deleteMessagesForSession(sid)
                dao.insertMessages(_messages.value)
                dao.updateSessionTimestamp(sid, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save messages to database", e)
            }
        }
        updateLocalSessionTimestamp(sid)
    }

    private fun markPendingStreamAsFailed() {
        streamTimeoutJob?.cancel()
        val list = _messages.value.toMutableList()
        var changed = false
        for (i in list.indices) {
            if (list[i].role == MessageRole.ASSISTANT && list[i].streaming) {
                val msg = list[i]
                val newContent = if (msg.content.isEmpty()) {
                    "消息传输失败，请重试"
                } else {
                    "${msg.content}\n\n—— 消息传输失败，请重试"
                }
                list[i] = msg.copy(content = newContent, streaming = false)
                changed = true
            }
        }
        if (changed) {
            _messages.value = list
            saveCurrentMessages()
        }
    }

    private fun updateLocalSessionTimestamp(sessionId: String) {
        _sessions.value = _sessions.value.map {
            if (it.sessionId == sessionId) it.copy(updatedAt = System.currentTimeMillis()) else it
        }
    }

    fun switchSession(sessionId: String) {
        Log.d(TAG, "Switch to session $sessionId")
        saveCurrentMessages()
        currentSessionId = sessionId
        _messages.value = sessionMessages[sessionId]?.toList() ?: emptyList()
    }

    private fun ensureSessionExists() {
        if (currentSessionId == null) {
            Log.d(TAG, "No active session, auto-creating one")
            newSession()
        }
    }

    fun newSession() {
        Log.d(TAG, "New session")
        saveCurrentMessages()
        val placeholderId = "local_${UUID.randomUUID().toString().take(8)}"
        val emptySession = ChatSession(sessionId = placeholderId, displayName = "新会话")
        _sessions.value = _sessions.value + emptySession
        sessionMessages[placeholderId] = mutableListOf()
        runBlocking(Dispatchers.IO) {
            try {
                dao.insertSession(emptySession)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save new session", e)
            }
        }
        currentSessionId = placeholderId
        _messages.value = emptyList()
    }

    fun currentSession(): ChatSession? {
        val sid = currentSessionId ?: return null
        return _sessions.value.find { it.sessionId == sid }
    }

    fun renameSession(sessionId: String, newName: String) {
        Log.d(TAG, "Rename session $sessionId -> $newName")
        _sessions.value = _sessions.value.map {
            if (it.sessionId == sessionId) it.copy(displayName = newName) else it
        }
        runBlocking(Dispatchers.IO) {
            try {
                dao.renameSession(sessionId, newName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename session in database", e)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        Log.d(TAG, "Delete session $sessionId")
        _sessions.value = _sessions.value.filter { it.sessionId != sessionId }
        sessionMessages.remove(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
            _messages.value = emptyList()
        }
        runBlocking(Dispatchers.IO) {
            try {
                dao.deleteMessagesForSession(sessionId)
                dao.deleteSession(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete session from database", e)
            }
        }
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
