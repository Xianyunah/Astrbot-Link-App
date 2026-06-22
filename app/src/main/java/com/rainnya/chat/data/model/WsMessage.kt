package com.rainnya.chat.data.model

data class WsOutgoingMessage(
    val t: String = "send",
    val message: Any,
    val username: String = "",
    val session_id: String? = null,
    val message_id: String? = null,
    val enable_streaming: Boolean = true,
)

data class WsIncomingMessage(
    val type: String = "",
    val data: Any? = null,
    val session_id: String? = null,
    val message_id: String? = null,
    val streaming: Boolean? = null,
    val code: String? = null,
    val attachment_id: String? = null,
    val url: String? = null,
)

data class WsMessageSegment(
    val type: String,
    val text: String? = null,
    val attachment_id: String? = null,
    val url: String? = null,
    val filename: String? = null,
    val mime_type: String? = null,
)
