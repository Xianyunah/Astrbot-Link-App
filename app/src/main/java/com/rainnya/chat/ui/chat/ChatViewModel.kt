package com.rainnya.chat.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.rainnya.chat.data.repository.ChatRepository
import com.rainnya.chat.data.repository.ConnectionState
import com.rainnya.chat.data.settings.AppSettings
import com.rainnya.chat.data.websocket.WsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<com.rainnya.chat.data.model.ChatMessage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val inputText: String = "",
    val pendingImageUri: Uri? = null,
    val uploading: Boolean = false,
    val uploadProgress: Float = 0f,
) {
    val isStreaming: Boolean
        get() = messages.any { it.streaming }
}

class ChatViewModel(application: Application) : AndroidViewModel(application), LifecycleEventObserver {
    private val settings = AppSettings(application)
    val repository = ChatRepository(viewModelScope, application, settings)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.messages.collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            repository.uploading.collect { uploading ->
                _uiState.value = _uiState.value.copy(uploading = uploading)
            }
        }
        viewModelScope.launch {
            repository.uploadProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(uploadProgress = progress)
            }
        }
        viewModelScope.launch {
            repository.wsEvents.collect { event ->
                repository.handleWsEvent(event)
            }
        }
        if (settings.isConfigured) {
            repository.connect()
        }
    }

    fun onResume() {
        val state = _uiState.value.connectionState
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            if (settings.isConfigured) {
                repository.reconnect()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.pendingImageUri == null) return
        val imageUri = _uiState.value.pendingImageUri
        if (imageUri != null) {
            repository.sendMessageWithImage(text.trim(), imageUri)
            _uiState.value = _uiState.value.copy(inputText = "", pendingImageUri = null)
        } else {
            repository.sendMessage(text.trim())
            _uiState.value = _uiState.value.copy(inputText = "")
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun addImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(pendingImageUri = uri)
    }

    fun removeImage() {
        _uiState.value = _uiState.value.copy(pendingImageUri = null)
    }

    fun newSession() {
        repository.newSession()
    }

    fun switchSession(sessionId: String) {
        repository.switchSession(sessionId)
    }

    fun reconnect() {
        if (settings.isConfigured) {
            repository.reconnect()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            onResume()
        }
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }
}
