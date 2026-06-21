package com.rainnya.chat.data.websocket

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rainnya.chat.data.model.WsIncomingMessage
import com.rainnya.chat.data.model.WsOutgoingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class WsEvent {
    data class MessageReceived(val msg: WsIncomingMessage) : WsEvent()
    data class Connected(val sessionId: String?) : WsEvent()
    data class Disconnected(val reason: String) : WsEvent()
    data class Error(val error: String) : WsEvent()
}

class AstrBotWsClient(
    private val gson: Gson = Gson(),
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _events = Channel<WsEvent>(Channel.BUFFERED)
    val events: Flow<WsEvent> = _events.receiveAsFlow()

    fun connect(wsUrl: String, apiKey: String) {
        val request = Request.Builder()
            .url("$wsUrl?api_key=$apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch { _events.send(WsEvent.Connected(null)) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val type = json.get("type")?.asString ?: ""

                    if (type == "pong") return

                    val msg = WsIncomingMessage(
                        type = type,
                        data = json.get("data")?.asString ?: json.get("data")?.toString(),
                        session_id = json.get("session_id")?.asString,
                        message_id = json.get("message_id")?.asString,
                        streaming = json.get("streaming")?.asBoolean,
                        code = json.get("code")?.asString,
                    )
                    scope.launch { _events.send(WsEvent.MessageReceived(msg)) }
                } catch (_: Exception) { }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                scope.launch { _events.send(WsEvent.Disconnected(reason)) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { _events.send(WsEvent.Disconnected(reason)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { _events.send(WsEvent.Error(t.message ?: "Unknown error")) }
            }
        })
    }

    fun sendMessage(
        text: String,
        username: String,
        sessionId: String? = null,
        messageId: String = UUID.randomUUID().toString(),
    ) {
        val msg = WsOutgoingMessage(
            message = text,
            username = username,
            session_id = sessionId,
            message_id = messageId,
        )
        webSocket?.send(gson.toJson(msg))
    }

    fun sendPing() {
        webSocket?.send(gson.toJson(mapOf("t" to "ping")))
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }
}
